(ns webhook-explorer.request-package
  (:require [clojure.string :as string]
            [clojure.set :as cset]
            [clojure.core.async :as async]
            [clojure.core.async.impl.protocols :as async-protos]
            [clojure.spec.alpha :as s]
            [webhook-explorer.specs.request-package]
            ["mustache" :as m]))

(defn chan? [c]
  "Tests if c is a core.async.chan"
  (satisfies?
   async-protos/WritePort
   c))

(defn parse-var [v]
  "Given a string, convert every.req.var or all.req.var to
   {:trigger :every
    :req \"req\"
    :template-var \"var\"}"
  (let [items (string/split v #"\.")]
    (when (>= (count items) 3)
      (-> (zipmap [:trigger :req :template-var] items)
          (update :trigger keyword)))))

(defn deps->dg [deps]
  (reduce
    (fn [dg [n ds]]
      (->> ds
           (into
             #{}
             (map :req))
           (assoc dg n)))
    {}
    deps))

(defn acyclical?
  ([deps]
   (let [deps (deps->dg deps)]
     (loop [n (ffirst deps)
            visited? #{}]
       (let [{:keys [res visited?]} (acyclical? deps visited? n)
             next-disconnected-node (first (cset/difference (set (keys deps)) visited?))]
         (cond
           (not res) false
           (some? next-disconnected-node) (recur next-disconnected-node #{})
           :else true)))))
  ([deps visited? n]
   (let [dependents (get deps n)
         new-visited? (conj visited? n)]
     (if (visited? n)
       {:res false :visited? new-visited?}
       (reduce
        (fn [{prev-res :res prev-visited? :visited?} n]
          (let [{this-res :res this-visited? :visited?} (acyclical? deps new-visited? n)
                next-visited? (cset/union prev-visited? this-visited?)]
            (if this-res
              {:res prev-res :visited? next-visited?}
              (reduced {:res this-res :visited? next-visited?}))))
        {:res true :visited? new-visited?}
        dependents)))))

(defn dependency-graph [{:keys [input-template-vars reqs]}]
  (->> reqs
       (mapcat
        (fn [{:keys [name]
              {:keys [qs headers body]} :req
              {headers-caps :headers
               {body-caps :captures} :body} :captures}]
          (->> (vals qs)
               (concat (vals headers))
               (concat [body])
               (map
                (fn [t]
                  {:req name
                   :template t})))))
       (reduce
        (fn [deps {source-req :req :keys [template]}]
          (->> (js->clj (m/parse template))
               ; looks like: [["#", "a", 0, 6, [["name", ".", 6, 11], 11]]]
               ; vector of [type, name, start-idx, end-idx, children]
               (tree-seq coll? identity)
               (filter coll?)
               (keep
                (fn [v]
                  (when (coll? v)
                    (let [[typ template-var _ _] v]
                      (when (#{"name" "&" "#" "^" ">"} typ)
                        (when-let [req-ref (parse-var template-var)]
                          {source-req #{req-ref}}))))))
               (apply merge-with into deps)))
        {})))

(s/def ::trigger #{:every :all})
(s/def ::req string?)
(s/def ::template-var string?)
(s/def ::dep
  (s/keys :req-un [::trigger ::req ::template-var]))
(s/fdef dependency-graph
  :args (s/cat :pkg :request-package/package)
  :ret (s/map-of
        string?
        (s/coll-of
         ::dep
         :kind set)))

(defn mult-sub [m]
  "Takes a clojure.core.async/mult and returns a channel subscribed to it."
  (let [c (async/chan)]
    (async/tap m c)
    c))

(defn all-ch [req ch]
  "Creates a channel that listens for values on ch for request, req
   and puts a map with :trigger, :req, :value keys to the output
   channel that it returns once ch is closed. :value contains
   a vector of all items emitted by ch."
  (let [out (async/chan)]
    (async/go-loop [acc []]
      (if-some [v (async/<! ch)]
        (recur (conj acc v))
        (do (async/>! out {:trigger :all
                           :req req
                           :value acc})
            (async/close! out))))
    out))

(defn wait-for-all-closed [chs]
  (let [out-ch (async/chan)
        m (async/merge chs)]
    (async/go-loop []
      (if (some? (async/<! m))
        (recur)
        (async/close! out-ch)))
    out-ch))

(defn run-pkg [{:keys [inputs exec]
                {:keys [reqs] :as pkg} :pkg}]
  "Given :inputs, :exec, and a :pkg, runs all requests in pkg with the given
   inputs by invoking the :exec function, providing req and dep-vals and
   expecting a channel to be returned."
  (let [dg (dependency-graph pkg)]
    (when-not (acyclical? dg)
      (throw (ex-info "Cyclical dependencies" {:anomaly :cyclical-dependencies :dg dg})))
    (let [ch-by-req (into
                     {}
                     (map
                      (fn [{:keys [name] :as req}]
                        (let [c (async/chan)
                              m (async/mult c)]
                          [name
                           {:req req
                            :ch c
                            :mult m}])))
                     reqs)
          dep->dep-chan (fn [{:keys [trigger] dep-req :req}]
                          (let [{:keys [mult]} (get ch-by-req dep-req)
                                ch (mult-sub mult)]
                            (if (= trigger :all)
                              (all-ch dep-req ch)
                              ch)))
          dep-chans-by-req (into
                            {}
                            (map
                             (fn [[req-name]]
                               [req-name (->> req-name (get dg) (mapv dep->dep-chan))]))
                            ch-by-req)]
      (doseq [[req-name {:keys [req ch]}] ch-by-req
              :let [deps (get dg req-name)
                    dep-ch (async/merge (get dep-chans-by-req req-name))]]
        (async/go-loop [dep-vals (or inputs {})]
          (if-some [{:keys [trigger value] dep-req :req} (async/<! dep-ch)]
            (let [dep-vals (assoc-in dep-vals [trigger dep-req] value)]
              (when (every?
                     #(->> %
                           ((juxt :trigger :req))
                           (get-in dep-vals)
                           some?)
                     deps)
                (async/>! ch {:trigger :every :req req-name :value (async/<! (exec req dep-vals))}))
              (recur dep-vals))
            (do
              (when (empty? deps)
                (async/>!
                 ch
                 {:trigger :every
                  :req req-name
                  :value (async/<! (exec req (or inputs {})))}))
              (async/close! ch)))))
      (wait-for-all-closed (->> ch-by-req
                                vals
                                (map :mult)
                                (map mult-sub))))))

(s/def ::inputs
  (s/map-of string? string?))
(s/def ::pkg :request-package/package)
(s/def ::exec
  (s/fspec
   :args (s/cat :req :request-package/req
                :dep-vals map?)
   :ret chan?))
(s/fdef run-pkg
  :args (s/cat :args (s/keys :req-un [::inputs ::pkg ::exec]))
  :ret chan?)
