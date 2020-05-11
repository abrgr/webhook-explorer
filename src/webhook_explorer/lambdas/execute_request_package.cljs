(ns webhook-explorer.lambdas.execute-request-package
  (:require [debux.cs.core :as d :refer-macros  [dbg dbgn]]
            [clojure.core.async :as async]
            [webhook-explorer.utils :as u]
            [webhook-explorer.lambdas.request-package-ops :as ops]
            [webhook-explorer.lambdas.handler :as h]))

(defmethod h/handler "/api/request-packages/{name}/execute"
  [event context]
  (let [out (async/chan)]
    (async/go
      (let [rp-name (get-in event [:path-parameters :name])
            rp (async/<! (ops/get-request-package rp-name))]
        (u/put-close!
          out
          {:is-base64-encoded false
           :status-code 200
           :body "Hello world"})))
    out))
