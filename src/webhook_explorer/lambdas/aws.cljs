(ns webhook-explorer.lambdas.aws
  (:require [cljs.nodejs :as njs]
            [debux.cs.core :as d :refer-macros  [dbg dbgn]]
            [clojure.core.async :as async]
            [goog.object :as obj]
            ["aws-sdk/clients/s3" :as S3]
            [webhook-explorer.utils :as u]))

(def s3 (S3. #js {:apiVersion "2019-09-21"}))
(def bucket (obj/getValueByKeys njs/process #js ["env" "BUCKET_NAME"]))

(defn pcatch [p c]
  (.catch p #(if (instance? js/Error %)
               (u/put-close! c %)
               (let [e (js/Error. "Error")]
                 (obj/set e "inner" %)
                 (u/put-close! c e)))))

(defn s3-get-object [key]
  (let [c (async/chan)]
    (-> s3
        (.getObject #js {:Bucket bucket :Key key})
        (.promise)
        (.then (comp (partial u/put-close! c)
                     #(.toString % "utf8")
                     #(obj/get % "Body")))
        (pcatch c))
    c))

(defn js-assoc [o k v]
  (obj/set o k v)
  o)

(defn s3-list-objects [{:keys [prefix token]}]
  (let [c (async/chan)
        opts (cond-> #js {:Bucket bucket
                          :Delimiter "/"
                          :Prefix prefix}
               token (js-assoc "ContinuationToken" token))]
    (-> s3
        (.listObjectsV2 opts)
        (.promise)
        (.then (fn [result]
                 (-> result
                     (js->clj :keywordize-keys true)
                     ((juxt :NextContinuationToken
                            #(into
                              (mapv :Key (:Contents %))
                              (map :Prefix)
                              (:CommonPrefixes %))))
                     (->> (zipmap [:next-token :items])
                          (u/put-close! c)))))
        (pcatch c))
    c))
