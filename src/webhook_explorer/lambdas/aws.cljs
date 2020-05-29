(ns webhook-explorer.lambdas.aws
  (:require [debux.cs.core :as d :refer-macros  [dbg dbgn]]
            [clojure.core.async :as async]
            [goog.object :as obj]
            ["aws-sdk/clients/s3" :as S3]
            ["aws-sdk/clients/sqs" :as SQS]
            [webhook-explorer.lambdas.env :as env]
            [webhook-explorer.utils :as u]))

(def cfg #js {:apiVersion "2019-09-21"})
(def s3 (S3. cfg))
(def sqs (SQS. cfg))

(defn pcatch [p c]
  (.catch p #(if (instance? js/Error %)
               (u/put-close! c %)
               (let [e (js/Error. "Error")]
                 (obj/set e "inner" %)
                 (u/put-close! c e)))))

(defn s3-get-object [key]
  (let [c (async/chan)]
    (-> s3
        (.getObject #js {:Bucket env/bucket :Key key})
        (.promise)
        (.then (comp (partial u/put-close! c)
                     #(.toString % "utf8")
                     #(obj/get % "Body")))
        (pcatch c))
    c))

(defn s3-put-object [{:keys [key body content-type]}]
  (let [c (async/chan)]
    (-> s3
        (.putObject #js {:Bucket env/bucket
                         :Key key
                         :ContentType (or content-type "application/json")
                         :Body body})
        (.promise)
        (.then #(async/close! c))
        (pcatch c))
    c))

(defn js-assoc [o k v]
  (obj/set o k v)
  o)

(defn s3-list-objects [{:keys [prefix token]}]
  (let [c (async/chan)
        opts (cond-> #js {:Bucket env/bucket
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

(defn sqs-send-batch
  ([items]
   (sqs-send-batch 0))
  ([start-id items]
   (let [c (async/chan)
         entries (->> items
                      (map-indexed
                       (fn [i {:keys [body delay-seconds path]}]
                         (cond-> #js {:Id (str (+ start-id i))
                                      :MessageAttributes #js {:path #js {:DataType "String"
                                                                         :StringValue path}}
                                      :MessageBody body}
                           delay-seconds (js-assoc "DelaySeconds" delay-seconds))))
                      clj->js)]
     (-> sqs
         (.sendMessageBatch #js {:QueueUrl env/execution-queue-url
                                 :Entries entries})
         (.promise)
         (.then (fn [result]
                  (-> result
                      (js->clj :keywordize-keys true)
                      (->> (u/put-close! c)))))
         (pcatch c))
     c)))

(defn sqs-put-all [items]
  (let [batch-size 10
        item-batches (partition-all batch-size items)]
    (->> item-batches
         (map-indexed
          (fn [idx batch]
            (sqs-send-batch (* batch-size idx) batch)))
         async/merge
         (async/into [])
         (u/async-xform
          (map
           (u/pass-errors
            (fn [results]
              (mapcat
               (fn [{:keys [Successful Failed] :as d}]
                 (when-not (empty? Failed)
                   (.warn js/console (clj->js {:msg "Failed to enqueue items"
                                               :failed-items Failed})))
                 (->> Failed
                      (map :Id)
                      (map #(get items (js/parseInt % 10)))))
               results)))))
         (u/async-xform
          (map
           (u/pass-errors
            (fn [failed]
              {:failed failed})))))))

