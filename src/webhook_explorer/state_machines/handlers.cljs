(ns webhook-explorer.state-machines.handlers
  (:require [webhook-explorer.xstate :as xs]
            [webhook-explorer.env :as env]
            [webhook-explorer.remote.handlers :as remote-handlers]
            [goog.object :as obj]))

(def machine
  (xs/machine
   {:cfg
    (xs/cfg->machine
     :handler
     '[* [[:reset -> :start ! :reset-params]]
       > :idle []
       :start [[*transient* -> :fetch-handler | :has-params]
               [*transient* -> :ready ! :set-default-handler]]
       :fetch-handler [[$ :fetch-handler
                        :on-done -> :ready ! :receive-handler
                        :on-error -> :failed ! :receive-handler-error]]
       :ready [[:update-handler -> *self* ! :update-handler]]
       :failed []])
    :opts
    {:ctx {:params nil
           :handler nil
           :error nil}
     :actions
     {:receive-handler
      (xs/assign-ctx-from-evt {:evt-prop :data
                               :ctx-prop :handler})
      :receive-handler-error
      (xs/assign-ctx-from-evt {:evt-prop :data
                               :ctx-prop :handler-error})
      :reset-params
      (xs/assign-ctx-from-evt {:evt-prop :params
                               :ctx-prop :params
                               :static-ctx {:handler nil
                                            :error nil}})
      :update-handler
      (xs/update-ctx-from-evt {:ctx-prop :handler
                               :updater-prop :updater})
      :set-default-handler
      (xs/assign-ctx {:ctx-prop :handler
                      :static-ctx {:proto :https
                                   :match-type :exact
                                   :path ""
                                   :method nil
                                   :domain (first env/handler-domains)
                                   :matchers []}})}
     :guards
     {:has-params (fn [ctx] (some? (obj/get ctx "params")))}
     :services
     {:fetch-handler (fn [ctx, evt]
                       (remote-handlers/get-handler
                        (obj/get ctx "params")))}}}))

(defn svc
  ([]
   (svc nil))
  ([opts]
   (xs/interpret-and-start machine opts)))
