(ns webhook-explorer.lambdas.execute-request-package
  (:require [clojure.core.async :as async]
            [webhook-explorer.lambdas.request-package-ops :as ops]
            [webhook-explorer.lambdas.handler :as h]))

(defmethod h/handler "/api/request-packages/{name}/execute"
  [event context]
  (async/go
    (let [rp-name (get-in event [:path-parameters :name])
          rp (async/<! (ops/get-request-package rp-name))]
      (println "RP!" rp)
      {:is-base64-encoded false
       :status-code 200
       :body "Hello world"})))
