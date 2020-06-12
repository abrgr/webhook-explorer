(ns webhook-explorer.state-machines.execute-package
  (:require [webhook-explorer.xstate :as xs]
            [webhook-explorer.env :as env]
            [webhook-explorer.remote.packages :as remote-pkgs]
            [webhook-explorer.nav-to :as nav-to]
            [goog.object :as obj]))

(def machine
  (xs/machine
   {:cfg
    (xs/cfg->machine
     :executions
     '[* [[:reset -> *self* ! :reset-params]]
       > :hidden [[:show-execution -> :loading]]
       :loading [[$ :load-package
                  :on-done -> :ready ! :store-package ! :reset-execution-inputs
                  :on-error -> :error ! :store-error]]
       :ready [[:cancel -> :hidden]
               [:execute -> :executing]
               [:update-execution-input -> *self* ! :update-execution-input]]
       :executing [[$ :execute
                    :on-done -> :ready ! :nav-to-execution
                    :on-error -> :error ! :store-error]]
       :error []])
    :opts
    {:ctx {:package nil
           :error nil
           :execution-inputs {}
           :params nil}
     :actions
     {:reset-params
      (xs/assign-ctx-from-evt {:evt-prop :params
                               :ctx-prop :params})
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
      :store-error
      (xs/assign-ctx-from-evt {:evt-prop :data
                               :ctx-prop :error})
      :update-execution-input
      (xs/xform-ctx-from-evt
       {:ctx-prop :execution-inputs}
       (fn [inputs {:keys [k v] :as e}]
         (assoc inputs k v)))
      :nav-to-execution
      (xs/->action
        (fn [ctx evt]
          (nav-to/go :package-execution (select-keys (:params ctx) [:name :id]))))}
     :services
     {:load-package (fn [{{:keys [name]} :params}]
                      (remote-pkgs/load-package {:name name}))
      :execute (fn [{:keys [execution-inputs] {:keys [name]} :params}]
                 (remote-pkgs/execute name {:inputs execution-inputs}))}}}))
