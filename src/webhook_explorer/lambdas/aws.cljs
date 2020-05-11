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

(defn s3-list-objects [prefix]
  (let [c (async/chan)]
    (-> s3
        (.listObjectsV2 #js {:Bucket bucket :Delimiter "/" :Prefix prefix})
        (.promise)
        (.then (fn [result]
                 (-> result
                     (js->clj :keywordize-keys true)
                     :Contents
                     (->> (map :Key)
                          (u/put-close! c)))))
        (pcatch c))
    c))
