(ns webhook-explorer.lambdas.execute-request-package
  (:require [clojure.core.async :as async]
            [webhook-explorer.lambdas.handler :as h]))

(defmethod h/handler "/api/request-packages/execute"
  [event context]
  (async/go
    {:is-base64-encoded false
     :status-code 200
     :body "Hello world"}))
