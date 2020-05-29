(ns webhook-explorer.lambdas.execute-request-package
  (:require [debux.cs.core :as d :refer-macros  [dbg dbgn]]
            [clojure.core.async :as async]
            [webhook-explorer.utils :as u]
            [webhook-explorer.lambdas.request-package-ops :as ops]
            [webhook-explorer.lambdas.handler :as h]))

(defmethod h/handler {:method "SQS"
                      :path "execute-request-package"}
  [{[{{:keys [execution-request-key]} :body}] :records} context]
  (->> execution-request-key
       ((fn [k]
         (->> k
              ops/get-execution-request
              (u/async-xform
                (map
                  (u/pass-errors
                    (fn [req]
                      (assoc req :execution-request-key k))))))))
       (u/async-xform
         (map
           (u/pass-errors
             (fn [{:keys [request-package-key inputs execution-request-key]}]
               (->> (ops/get-request-package-by-key request-package-key)
                    (u/async-xform
                      (map
                        (u/pass-errors
                          (fn [rp]
                            {:rp rp
                             :inputs inputs
                             :execution-request-key execution-request-key})))))))))
       u/async-unwrap
       (u/async-xform
         (map
           (u/pass-errors
             (fn [{:keys [rp inputs execution-request-key]}]
               (->> {:rp rp :inputs inputs}
                    ops/execute
                    (u/async-xform
                      (map
                        (u/pass-errors
                          (fn [result]
                            {:result result
                             :execution-request-key execution-request-key})))))))))
       u/async-unwrap
       (u/async-xform
         (map
           (u/pass-errors
             (fn [x]
               (ops/write-execution-result x)))))
       u/async-unwrap))
