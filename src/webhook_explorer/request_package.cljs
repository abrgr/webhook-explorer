(ns webhook-explorer.request-package
  (:require [clojure.string :as string]
            ["mustache" :as m]))

(defn parse-var [v]
  "Given a string, convert every.req.var or all.req.var to
   {:trigger :every
    :req \"req\"
    :template-var \"var\"}"
  (let [items (string/split v #"\.")]
    (when (>= (count items) 3)
      (-> (zipmap [:trigger :req :template-var] items)
          (update :trigger keyword)))))

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
