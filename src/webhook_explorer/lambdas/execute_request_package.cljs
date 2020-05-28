(ns webhook-explorer.lambdas.execute-request-package
  (:require [debux.cs.core :as d :refer-macros  [dbg dbgn]]
            [clojure.core.async :as async]
            [webhook-explorer.utils :as u]
            [webhook-explorer.lambdas.request-package-ops :as ops]
            [webhook-explorer.lambdas.handler :as h]))

(defmethod h/handler {:method "POST"
                      :path "/api/request-packages/{name}/execute"}
  [event context]
  (let [out (async/chan)]
    (async/go
      (u/let+ [rp-name (-> event
                           (get-in [:path-parameters :name])
                           (js/decodeURIComponent))
               input-params (-> event (get :body) js->clj)
               rp (async/<! (ops/get-request-package rp-name))
               :abort [(instance? js/Error rp) (u/put-close! out rp)] 
               res (async/<! (ops/execute rp input-params))
               :abort [(instance? js/Error res) (u/put-close! out res)]]
        (u/put-close!
         out
         {:is-base64-encoded false
          :status-code 200
          :body {:rp rp
                 :res res}})))
    out))

(defmethod h/handler {:method "POST"
                      :path "/api/request-packages/{name}/executions"}
  [event context]
  (let [rp-name (-> event
                    (get-in [:path-parameters :name])
                    (js/decodeURIComponent))
        uid (get-in event [:request-context :authorizer :claims :uid])
        input-params (-> event (get :body) js->clj)]
    (u/async-xform
      (map
        (u/pass-errors
          (fn [res]
            {:is-base64-encoded false
             :status-code 200
             :body res})))
      (ops/write-execution-set {:request-package-name rp-name
                                :uid uid
                                :inputs input-params}))))
