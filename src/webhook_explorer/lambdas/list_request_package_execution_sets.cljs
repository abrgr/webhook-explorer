(ns webhook-explorer.lambdas.list-request-package-execution-sets
  (:require [debux.cs.core :as d :refer-macros  [dbg dbgn]]
            [clojure.core.async :as async]
            [webhook-explorer.utils :as u]
            [webhook-explorer.lambdas.request-package-ops :as ops]
            [webhook-explorer.lambdas.handler :as h]))

(defmethod h/handler {:method "GET"
                      :path "/api/request-packages/{name}/executions"}
  [event context]
  (let [token (get-in event [:query-string-parameters :token])
        rp-name (-> event
                    (get-in [:path-parameters :name])
                    (js/decodeURIComponent))]
    (u/async-xform
      (map
        (u/pass-errors
          (fn [res]
            {:is-base64-encoded false
             :status-code 200
             :body res})))
      (ops/list-execution-sets {:request-package-name rp-name :token token}))))
