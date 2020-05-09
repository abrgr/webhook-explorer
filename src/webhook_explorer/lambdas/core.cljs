(ns webhook-explorer.lambdas.core
  (:require [clojure.core.async :as async]
            [camel-snake-kebab.core :as csk]
            [camel-snake-kebab.extras :as cske]
            [webhook-explorer.promise-utils :as putil]
            [webhook-explorer.lambdas.handler :as h]
            [webhook-explorer.lambdas.execute-request-package]))

(defn ->clj [js]
  (-> js
      (js->clj :keywordize-keys true)
      (->> (cske/transform-keys csk/->kebab-case-keyword))))

(defn async-xform [xf in-ch]
  (let [out-ch (async/chan 1 xf)]
    (async/pipe in-ch out-ch)
    out-ch))

(defn handler [event context]
  (let [evt (->clj event)
        ctx (->clj context)]
    (println "handler" {:evt evt :ctx ctx})
    (->> (h/handler (->clj event) (->clj context))
         (async-xform (comp
                       (map (partial cske/transform-keys csk/->camelCase))
                       (map clj->js)))
         putil/chan->promise)))
