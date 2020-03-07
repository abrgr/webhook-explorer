(ns webhook-explorer.state-machines.handlers
  (:require [webhook-explorer.xstate :as xs]
            [webhook-explorer.env :as env]
            [webhook-explorer.remote.handlers :as remote-handlers]
            [goog.object :as obj]))

(def machine
  (xs/machine
   {:cfg
    {:id :handler
     :initial :idle
     :context {:params nil
               :handler nil
               :error nil}
     :on {:reset {:target :start
                  :actions :reset-params}}
     :states {:idle {}
              :start {:on {"" [{:target :fetch-handler
                                :cond :has-params}
                               {:target :ready
                                :actions :set-default-handler}]}}
              :fetch-handler {:invoke {:id :fetch-handler
                                       :src :fetch-handler
                                       :onDone {:target :ready
                                                :actions :receive-handler}
                                       :onError {:target :failed
                                                 :actions :receive-handler-error}}}
              :ready {:on {:update-handler {:actions :update-handler}}}
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
   (-> machine
       (xs/interpret machine opts)
       (.start)
       (.onTransition js/console.log))))
