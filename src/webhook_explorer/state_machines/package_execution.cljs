(ns webhook-explorer.state-machines.package-execution
  (:require [webhook-explorer.xstate :as xs]
            [webhook-explorer.env :as env]
            [webhook-explorer.remote.packages :as remote-pkgs]
            [webhook-explorer.state-machines.execute-package :as execpkg-machine]
            [goog.object :as obj]))

; TODO: machine just copied from package-executions
(def machine
  (xs/machine
   {:cfg
    (xs/cfg->machine
     :execution
     '[> :init [[*transient* -> :ready ! :spawn-executor]]
       :ready [[:reset -> *ext*/*self* ! :reset-params ! :reset-exec-params]
               [:load-executions -> :loading]
               [:show-execution -> *self* ! :send-to-exec]
               [:execute -> *self* ! :send-to-exec]
               [:update-execution-input -> *self* ! :send-to-exec]
               [:cancel-execution -> *self* ! :send-to-exec]]
       :error [[:load-executions -> :loading]]
       :loading [[$ :load-executions
                  :on-done -> :ready ! :store-executions ! :store-next-req
                  :on-error -> :error ! :store-error]]])
    :opts
    {:ctx {:execute-ref nil
           :executions []
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
      :reset-exec-params
      (xs/send-event
       (fn [_ {:keys [params]}]
         {:type :reset
          :params params})
       :execute-ref)
      :spawn-executor
      (xs/xform-ctx-from-evt
       {:ctx-prop :execute-ref}
       (fn [_ _]
         (xs/spawn execpkg-machine/machine #js {:sync true})))
      :send-to-exec
      (xs/send-event
       (fn [_ event] event)
       :execute-ref)
      :store-package
      (xs/assign-ctx-from-evt {:evt-prop :data
                               :ctx-prop :package})
      :reset-execution-inputs
      (xs/xform-ctx-from-evt
       {:ctx-prop :execution-inputs}
       (fn [_ {{:keys [input-template-vars]} :data}]
         (into
          {}
          (map (fn [v] {v ""}))
          input-template-vars)))
      :store-package-error
      (xs/assign-ctx-from-evt {:evt-prop :data
                               :ctx-prop :package-error})
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
                         (remote-pkgs/load-executions name next-req))
      :load-package (fn [{{:keys [name]} :params}]
                      (remote-pkgs/load-package {:name name}))
      :execute (fn [{:keys [execution-inputs] {:keys [name]} :params}]
                 (remote-pkgs/execute name {:inputs execution-inputs}))}}}))

(defn svc
  ([]
   (svc nil))
  ([opts]
   (xs/interpret-and-start machine opts)))
