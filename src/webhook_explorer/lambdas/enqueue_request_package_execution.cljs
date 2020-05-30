(ns webhook-explorer.lambdas.enqueue-request-package-execution
  (:require [debux.cs.core :as d :refer-macros  [dbg dbgn]]
            [clojure.core.async :as async]
            [webhook-explorer.utils :as u]
            [webhook-explorer.lambdas.request-package-ops :as ops]
            [webhook-explorer.lambdas.handler :as h]))

(defmethod h/handler {:method "POST"
                      :path "/api/request-packages/{name}/executions"}
  [event context]
  (let [rp-name (-> event
                    (get-in [:path-parameters :name])
                    (js/decodeURIComponent))
        uid (get-in event [:request-context :authorizer :claims :uid])
        input-params (get-in event [:body :request-package-execution :inputs])]
    (->> (ops/get-request-package rp-name)
         (u/async-xform
          (map
           (u/pass-errors
            (fn [rp]
              (ops/write-execution-set {:request-package rp
                                        :uid uid
                                        :inputs input-params})))))
         u/async-unwrap
         (u/async-xform
          (map
           (u/pass-errors
            (fn [res]
              {:is-base64-encoded false
               :status-code 200
               :body {:request-package-execution res}})))))))
