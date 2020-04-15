(ns webhook-explorer.state-machines.edit-package
  (:require [webhook-explorer.xstate :as xs]
            [webhook-explorer.env :as env]
            [goog.object :as obj]))

(def machine
  (xs/machine
   {:cfg
    (xs/cfg->machine
     :edit-package
     '[* [[:reset -> :start ! :reset-params]]
       > :idle []
       :start [[*transient* -> :ready ! :set-default-package]]
       :ready [[:update-package -> *self* ! :update-package]]])
    :opts
    {:ctx {}
     :actions
     {:reset-params
      (xs/assign-ctx-from-evt {:evt-prop :params
                               :ctx-prop :params
                               :static-ctx {:package nil
                                            :error nil}})
      :set-default-package
      (xs/assign-ctx {:ctx-prop :package
                      :static-ctx {:reqs []}})
      :update-package
      (xs/update-ctx-from-evt {:ctx-prop :package
                               :updater-prop :updater})}}}))

(defn svc
  ([]
   (svc nil))
  ([opts]
   (xs/interpret-and-start machine opts)))
