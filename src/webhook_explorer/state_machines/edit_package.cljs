(ns webhook-explorer.state-machines.packages
  (:require [webhook-explorer.xstate :as xs]
            [webhook-explorer.env :as env]
            [webhook-explorer.remote.packages :as remote-handlers]
            [goog.object :as obj]))

(def machine
  (xs/machine
   {:cfg
    {:id :edit-package
     :initial :idle
     :context {:params nil
               :package nil
               :error nil}
     :on {:reset {:target :start
                  :actions :reset-params}}
     :states {:idle {}
              :start {:on {"" [{:target :fetch-package
                                :cond :has-params}
                               {:target :ready
                                :actions :set-default-package}]}}
              :fetch-package {:invoke {:id :fetch-package
                                       :src :fetch-package
                                       :onDone {:target :ready
                                                :actions :receive-package}
                                       :onError {:target :failed
                                                 :actions :receive-package-error}}}
              :ready {:on {:update-package {:actions :update-package}}}
              :failed {}}}
    :opts
    {}}))

(defn svc
  ([]
   (svc nil))
  ([opts]
   (xs/interpret-and-start machine opts)))
