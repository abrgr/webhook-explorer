(ns webhook-explorer.state-machines.package-executions
  (:require [webhook-explorer.xstate :as xs]
            [webhook-explorer.env :as env]
            [webhook-explorer.remote.packages :as remote-pkgs]
            [goog.object :as obj]))

(def machine
  (xs/machine
   {:cfg
    (xs/cfg->machine
     :executions
     '[> :ready [[:reset -> *ext*/*self* ! :reset-params]
                 (children
                  > :regular [[:load-executions -> :loading]]
                  :error [[:load-executions -> :loading]]
                  :loading [[$ :load-executions
                             :on-done -> :regular ! :store-executions ! :store-next-req
                             :on-error -> :error ! :store-error]])]])
    :opts
    {:ctx {:executions []
           :params nil
           :next-req {}
           :error nil}
     :actions
     {:reset-params
      (xs/assign-ctx-from-evt {:evt-prop :params
                               :ctx-prop :params
                               :static-ctx {:executions []
                                            :next-req {}
                                            :error nil}})
      :store-executions
      (xs/assign-ctx-from-evt
       {:ctx-prop :executions
        :evt-path [:data :execution-sets]})
      :store-next-req
      (xs/xform-ctx-from-evt
       {:ctx-prop :next-req}
       (fn [_ {{:keys [next-token]} :data}]
         (when next-token
           {:token next-token})))
      :store-error
      (xs/assign-ctx-from-evt
       {:ctx-prop :error
        :evt-prop :data})}
     :services
     {:load-executions (fn [{:keys [next-req] {:keys [name]} :params}]
                         (remote-pkgs/load-executions name next-req))}}}))

(defn svc
  ([]
   (svc nil))
  ([opts]
   (xs/interpret-and-start machine opts)))
