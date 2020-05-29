(ns webhook-explorer.lambdas.get-request-package
  (:require [debux.cs.core :as d :refer-macros  [dbg dbgn]]
            [clojure.core.async :as async]
            [webhook-explorer.utils :as u]
            [webhook-explorer.lambdas.request-package-ops :as ops]
            [webhook-explorer.lambdas.handler :as h]))

(defmethod h/handler {:method "GET"
                      :path "/api/request-packages/{name}"}
  [event context]
  (let [out (async/chan)]
    (async/go
      (u/let+ [rp-name (-> event
                           (get-in [:path-parameters :name])
                           (js/decodeURIComponent))
               rp (async/<! (ops/get-request-package rp-name))
               :abort [(instance? js/Error rp) (u/put-close! out rp)]]
              (u/put-close!
               out
               {:is-base64-encoded false
                :status-code 200
                :body {:request-package rp}})))
    out))
