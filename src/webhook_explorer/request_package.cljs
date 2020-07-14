(ns webhook-explorer.request-package
  (:require [debux.cs.core :refer-macros [dbg dbgn]]
            [clojure.string :as string]
            [clojure.set :as cset]
            [clojure.core.async :as async]
            [clojure.spec.alpha :as s]
            [webhook-explorer.specs.request-package]
            [webhook-explorer.specs.chan :as c]
            [webhook-explorer.utils :as u]
            ["mustache" :as m]
            ["jsonpath" :as jp]))

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
                          {source-req #{(assoc req-ref :plural (= typ "#"))}}))))))
               (apply merge-with into deps)))
        {})))

(s/def ::trigger #{:every :all})
(s/def ::req string?)
(s/def ::template-var string?)
(s/def ::plural boolean?)
(s/def ::trigger+dep
  (s/keys :req-un [::trigger ::req]))
(s/def ::dep
  (s/keys :req-un [::trigger ::req ::template-var ::plural]))
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

(defn everys->all [everys]
  (reduce
   (fn [all every-val]
     (reduce
      (fn [all [k v]]
        (update
         all
         k
         (fn [prev]
           (into
            (or prev [])
            (if (coll? v) v [v])))))
      all
      every-val))
   {}
   (mapv :value everys)))

(defn all-ch [next-id req-name ch]
  "Creates a channel that listens for values on ch for request, req-name
   and puts a map with :trigger, :req, :value keys to the output
   channel that it returns once ch is closed. :value contains
   a vector of all items emitted by ch."
  (let [out (async/chan)]
    (async/go-loop [acc []]
      (if-some [v (async/<! ch)]
        (recur (conj acc v))
        (do (async/>! out {:trigger :all
                           :req req-name
                           :value (everys->all acc)
                           :path (-> acc
                                     first
                                     :path
                                     butlast
                                     vec
                                     (conj {:req req-name :trigger :all :id (next-id)}))})
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

(defn get-dep-vals [path trigger+dep->path->vals]
  "Get the vals map for each dep in dep->path->vals that matches a prefix of path.

   Example dep->path->vals:

    {{:trigger :every :req \"a\"}
      {[{:req \"a\" :trigger :every :id \"1\"}] {\"x\" [2 3 4]}}
     {:trigger :all :req \"b\"}
      {[{:req \"a\" :trigger :every :id \"1\"}
        {:req \"b\" :trigger :every :id \"2\"}] {\"y\" 2}
       [{:req \"a\" :trigger :every :id \"1\"}
        {:req \"b\" :trigger :every :id \"3\"}] {\"y\" 3}
       [{:req \"a\" :trigger :every :id \"1\"}
        {:req \"b\" :trigger :every :id \"4\"}] {\"y\" 4}}}"
  (->> trigger+dep->path->vals
       (keep
        (fn [[dep path->val]]
          (some->> path->val
                   (filter
                    (fn [[dep-path val]]
                      (->> dep-path
                           (interleave path) ; key to this is that interleave throws away remainder of larger coll 
                           (partition 2)
                           (every? (partial apply =)))))
                   first
                   second
                   (vector dep))))
       (into {})))

(defn cartesian-product [m]
  "Given a map of domain to range, with range as a coll?,
   return a coll? of a map from domain to items of range where each
   map returned has values from the cartesian product of the inputs."
  (if (empty? m)
    (list {})
    (let [[k vs] (first m)]
      (for [v vs
            m' (cartesian-product (dissoc m k))]
        (assoc m' k v)))))

(defn flattened-template-data [req-deps trigger+dep->vals]
  (reduce
   (fn [acc {:keys [trigger req template-var plural]}]
     (assoc
      acc
      (str (name trigger) "." req "." template-var)
      (get-in trigger+dep->vals [{:trigger trigger :req req} template-var])))
   {}
   req-deps))

(defn flattened-template-data->template-data [ftd]
  (reduce
   (fn [acc [k v]]
     (assoc-in acc (string/split k #"\." 3) v))
   {}
   ftd))

(defn plural-vars [req-deps]
  (into
   #{}
   (comp (filter :plural)
         (map #(select-keys % [:trigger :req :template-var])))
   req-deps))

(defn dep-val-seq [req-deps inputs trigger+dep->vals]
  "Given a coll? of required dependencies, an input map, and a map from
   dependencies to values, where values can be items or collections of items,
   return a seq? of value maps, filtering out
   any value maps that do not provide the required dependencies."
  (when (every?
         (partial contains? trigger+dep->vals)
         (map #(select-keys % [:trigger :req]) req-deps))
    (let [td (flattened-template-data req-deps trigger+dep->vals)
          is-plural? (plural-vars req-deps)
          d->vs (group-by
                 (comp (every-pred
                        (comp coll? second)
                        (comp
                         not
                         is-plural?
                         #(update % :trigger keyword)
                         (partial zipmap [:trigger :req :template-var])
                         #(string/split % #"\." 3)
                         first))) td)
          manies (into {} (get d->vs true))
          singles (into {} (get d->vs false))]
      (map
       (fn [ftd]
         (apply merge inputs (flattened-template-data->template-data ftd)))
       (if (empty? manies)
         (list singles)
         (->> manies
              cartesian-product
              (map (partial merge singles))))))))

(s/fdef dep-val-seq
  :args (s/cat :req-deps (s/coll-of ::dep)
               :inputs (s/map-of string? string?)
               :trigger+dep->vals (s/map-of
                                   ::trigger+dep
                                   (s/map-of
                                    string?
                                    (s/or :single string?
                                          :many (s/coll-of string?)))))
  :ret (s/coll-of (s/map-of string? string?)))

(defn render [this template-values]
  (cond
    (string? this) (m/render this (clj->js template-values))
    (map? this) (into
                 {}
                 (map
                  (fn [[k v]]
                    [(if (string? k) (render k template-values) k)
                     (render v template-values)]))
                 this)))

(defn body->captures [body-caps body]
  (let [js-body (clj->js body)]
    (into
     {}
     (map
      (fn [[json-path {:keys [template-var]}]]
        [template-var (js->clj (.query jp js-body (name json-path)))]))
     body-caps)))

(defn headers->captures [headers-caps headers]
  (into
   {}
   (map
    (fn [[header {:keys [template-var]}]]
      [template-var (get headers header)]))
   headers-caps))

(defn status->captures [{:keys [template-var]} status]
  (when template-var
    {template-var (str status)}))

(defn resp-chan->captures [{{{body-caps :captures} :body
                             headers-caps :headers
                             status-caps :status} :captures} resp-ch]
  (u/async-xform
   (map
    (fn [{:keys [body headers status] :as r}]
      (merge
       {}
       (status->captures status-caps status)
       (body->captures body-caps body)
       (headers->captures headers-caps headers))))
   resp-ch))

(defn exec-req [exec req dep-vals]
  (->> req
       (into
        {}
        (map (fn [[k v]] [k (render v dep-vals)])))
       (#(do (println "pre-req" % req) %))
       exec
       (resp-chan->captures req)))

(s/fdef exec-req
  :args (s/cat :exec fn?
               :req :request-package/req
               :dep-vals map?)
  :ret c/chan?)

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
          id (atom 0)
          next-id (fn []
                    (swap! id inc)
                    @id)
          dep->dep-chan (fn [{:keys [trigger] dep-req :req}]
                          (let [{:keys [mult]} (get ch-by-req dep-req)
                                ch (mult-sub mult)]
                            (if (= trigger :all)
                              (all-ch next-id dep-req ch)
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
        (async/go-loop [trigger+dep->path->vals {}]
          (if-some [{:keys [path trigger value] dep-req :req} (async/<! dep-ch)]
            (if (->> deps
                     (map (juxt :req :trigger))
                     (some (partial = [dep-req trigger])))
              (let [trigger+dep->path->vals (assoc-in trigger+dep->path->vals [{:trigger trigger :req dep-req} path] value)
                    dep->vals (get-dep-vals path trigger+dep->path->vals)
                    matched-dep-vals (dep-val-seq deps inputs dep->vals)]
                (doseq [dep-vals matched-dep-vals
                        :let [value (async/<! (exec-req exec req dep-vals))]]
                  (async/>! ch {:trigger :every
                                :req req-name
                                :value value
                                :path (conj path {:req req-name :trigger :every :id (next-id)})}))
                (recur trigger+dep->path->vals))
              (recur trigger+dep->path->vals))
            (do
              (when (empty? deps)
                (let [value (async/<! (exec-req exec req (or inputs {})))]
                  (async/>!
                   ch
                   {:trigger :every
                    :req req-name
                    :value value
                    :path [{:req req-name :trigger :every :id (next-id)}]})))
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
   :ret c/chan?))
(s/fdef run-pkg
  :args (s/cat :args (s/keys :req-un [::inputs ::pkg ::exec]))
  :ret c/chan?)
