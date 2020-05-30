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

(defn xform-sqs-event [{[{:keys [event-source message-attributes]}] :records :as evt}]
  (if (= event-source "aws:sqs")
    (-> evt
        (assoc :http-method "SQS")
        (assoc :resource (get-in message-attributes [:path :string-value])))
    evt))

(defn xform-json-body [evt]
  (-> evt
      (update :body u/json->kebab-clj)
      (update :records (partial map #(update % :body u/json->kebab-clj)))))

(def xform-event (comp xform-sqs-event xform-json-body))

(defn handler [event context cb]
  (let [evt (-> event u/js->kebab-clj xform-event)
        ctx (u/js->kebab-clj context)]
    (->> (h/handler evt ctx)
         (u/async-xform
          (map
           (fn [res]
             (if (map? res)
               (-> res
                   (->> (cske/transform-keys csk/->camelCase))
                   (update :body (comp js/JSON.stringify clj->js))
                   (update :headers (partial merge default-headers))
                   clj->js)
               (do (.error js/console #js {:msg "Execution error" :error res})
                   (make-err-response res))))))
         (u/async-do (partial send-result cb)))
    nil))
