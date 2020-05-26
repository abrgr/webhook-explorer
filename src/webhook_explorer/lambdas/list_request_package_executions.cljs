(ns webhook-explorer.lambdas.list-request-package-executions
  (:require [debux.cs.core :as d :refer-macros  [dbg dbgn]]
            [clojure.core.async :as async]
            [webhook-explorer.utils :as u]
            [webhook-explorer.lambdas.request-package-ops :as ops]
            [webhook-explorer.lambdas.handler :as h]))

(defmethod h/handler {:method "GET"
                      :path "/api/request-packages/{name}/executions"}
  [event context]
  (let [out (async/chan)]
    (async/go
      (u/let+ [token (get-in event [:query-string-parameters :token])
               rp-name (-> event
                           (get-in [:path-parameters :name])
                           (js/decodeURIComponent))
               res (async/<! (ops/list-request-package-executions {:request-package-name rp-name :token token}))
               :abort [(instance? js/Error res) (u/put-close! out res)]]
        (u/put-close!
         out
         {:is-base64-encoded false
          :status-code 200
          :body res})))
    out))
