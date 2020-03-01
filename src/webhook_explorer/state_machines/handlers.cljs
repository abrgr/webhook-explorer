(ns webhook-explorer.state-machines.handlers
  (:require [webhook-explorer.xstate :as xs]))

(def machine
  (xs/machine
    {:cfg
      {:id :handler
       :initial :idle
       :context {:params nil
                 :handler nil
                 :error nil}
       :on {
         :reset {:actions []}}
       :states {
         :idle {}
         :start {
           :on {
             "" [{:target :fetch-handler
                  :cond :has-params}
                 {:target :ready}]}}
         :fetch-handler {
           :invoke {
             :id :fetch-handler
             :src :fetch-handler
             :onDone {:target :ready
                      :actions :receive-handler}
             :onError {:target :failed
                       :actions :receive-handler-error}}}
         :ready {}
         :failed {}}}
     :opts
      {:actions
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
                                                :error nil}})}
       :guards
        {:has-params (fn [ctx] (contains? ctx :params))}
       :services
        {:fetch-handler (fn [ctx, evt] nil)}}}))

(defn svc
  ([]
    (svc nil))
  ([opts]
    (-> machine
        (xs/interpret machine opts)
        (.start))))
