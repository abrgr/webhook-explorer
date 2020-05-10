(ns webhook-explorer.lambdas.aws
  (:require [cljs.nodejs :as njs]
            [clojure.core.async :as async]
            [goog.object :as obj]
            ["aws-sdk/clients/s3" :as S3]
            [webhook-explorer.utils :as u]))

(def s3 (S3. #js {:apiVersion "2019-09-21"}))
(def bucket (obj/getValueByKeys njs/process #js ["env" "BUCKET_NAME"]))

(defn s3-get-object [key]
  (let [c (async/chan)]
    (-> s3
        (.getObject #js {:Bucket bucket :Key key})
        (.promise)
        (.then (comp (partial u/put-close! c)
                     #(obj/get % "Body")))
        (.catch (partial u/put-close! c)))
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
        (.catch (partial u/put-close! c)))
    c))
