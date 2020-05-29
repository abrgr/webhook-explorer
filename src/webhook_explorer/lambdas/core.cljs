(ns webhook-explorer.lambdas.core
  (:require [clojure.core.async :as async]
            [camel-snake-kebab.core :as csk]
            [camel-snake-kebab.extras :as cske]
            [webhook-explorer.utils :as u]
            [webhook-explorer.promise-utils :as putil]
            [webhook-explorer.lambdas.handler :as h]
            [webhook-explorer.lambdas.get-request-package]
            [webhook-explorer.lambdas.enqueue-request-package-execution]
            [webhook-explorer.lambdas.list-request-packages]
            [webhook-explorer.lambdas.list-request-package-execution-sets]
            [webhook-explorer.lambdas.execute-request-package]))

(defn ->clj [js]
  (-> js
      (js->clj :keywordize-keys true)
      (->> (cske/transform-keys csk/->kebab-case-keyword))))

(defn send-result [cb res]
  (if (instance? js/Error res)
    (cb res)
    (cb nil res)))

(def default-headers
  {"Content-Type" "application/json"
   "Access-Control-Allow-Origin" "*"
   "Access-Control-Allow-Methods" "*"
   "Access-Control-Allow-Headers" "*"})

(defn make-err-response [err]
  #js {:isBase64Encoded false
       :statusCode 500
       :body (js/JSON.stringify #js {:error "Unknown"})})

(defn xform-sqs-event [{:keys [event-source event-source-arn] :as evt}]
  (if (= event-source "aws:sqs")
    (-> evt
        (assoc :http-method "SQS")
        (assoc :resource event-source-arn))
    evt))

(defn xform-json-body [evt]
  (update evt :body #(some-> % (js/JSON.parse) ->clj)))

(def xform-event (comp xform-sqs-event xform-json-body))

(defn handler [event context cb]
  (let [evt (->clj event)
        ctx (->clj context)]
    (->> (h/handler (xform-event (->clj event)) (->clj context))
         (u/async-xform
          (map
           (fn [res]
             (if (map? res)
               (-> res
                   (->> (cske/transform-keys csk/->camelCase))
                   (update :body (comp js/JSON.stringify clj->js))
                   (update :headers (partial merge default-headers))
                   clj->js)
               (make-err-response res)))))
         (u/async-do (partial send-result cb)))
    nil))
