(ns webhook-explorer.state-machines.packages
  (:require [webhook-explorer.xstate :as xs]
            [webhook-explorer.env :as env]
            [webhook-explorer.remote.packages :as remote-pkgs]
            [goog.object :as obj]))

(def machine
  (xs/machine
   {:cfg
    (xs/cfg->machine
     :packages
     '[> :ready [[:reset -> *ext*/*self*]
                 (children
                  > :regular [[:load-packages -> :loading]]
                  :error [[:load-packages -> :loading]]
                  :loading [[$ :load-packages
                             :on-done -> :regular ! :store-packages ! :store-next-req
                             :on-error -> :error ! :store-error]])]])
    :opts
    {:ctx {:packages []
           :next-req {}
           :error nil}
     :actions
     {:store-packages
      (xs/assign-ctx-from-evt
       {:ctx-prop :packages
        :evt-path [:data :request-packages]})
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
     {:load-packages (fn [{:keys [next-req]}]
                       (remote-pkgs/load-packages next-req))}}}))

(defn svc
  ([]
   (svc nil))
  ([opts]
   (xs/interpret-and-start machine opts)))
