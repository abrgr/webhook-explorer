(ns webhook-explorer.lambdas.core
  (:require [clojure.core.async :as async]
            [camel-snake-kebab.core :as csk]
            [camel-snake-kebab.extras :as cske]
            [webhook-explorer.utils :as u]
            [webhook-explorer.promise-utils :as putil]
            [webhook-explorer.lambdas.handler :as h]
            [webhook-explorer.lambdas.execute-request-package]
            [webhook-explorer.lambdas.list-request-packages]))

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

(defn handler [event context cb]
  (let [evt (->clj event)
        ctx (->clj context)]
    (->> (h/handler (->clj event) (->clj context))
         (u/async-xform (comp
                         (map (partial cske/transform-keys csk/->camelCase))
                         (map #(update % :body (comp js/JSON.stringify clj->js)))
                         (map (fn [x]
                                (update x :headers #(merge default-headers %))))
                         (map clj->js)))
         (u/async-do (partial send-result cb)))
    nil))
