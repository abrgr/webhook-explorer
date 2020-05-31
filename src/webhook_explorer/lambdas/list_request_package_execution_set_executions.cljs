(ns webhook-explorer.lambdas.list-request-package-execution-set-executions
  (:require [debux.cs.core :as d :refer-macros  [dbg dbgn]]
            [clojure.core.async :as async]
            [webhook-explorer.utils :as u]
            [webhook-explorer.lambdas.request-package-ops :as ops]
            [webhook-explorer.lambdas.handler :as h]))

(defmethod h/handler {:method "GET"
                      :path "/api/request-packages/{name}/executions/{id}"}
  [{{:keys [token]} :query-string-parameters
    {:keys [name uid date id]} :path-parameters} context]
  (u/async-xform
   (map
    (u/pass-errors
     (fn [res]
       {:is-base64-encoded false
        :status-code 200
        :body res})))
   (ops/list-execution-set-executions
    {:request-package-name name
     :execution-set-id id
     :token token})))
