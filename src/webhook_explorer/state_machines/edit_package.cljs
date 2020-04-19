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
       :ready [[:add-req -> *self* ! :add-req]
               [:update-header-capture -> *self* ! :update-header-capture]
               [:remove-header-capture -> *self* ! :remove-header-capture]
               [:remove-all-body-captures -> *self* ! :remove-all-body-captures]
               [:update-body-capture-type -> *self* ! :update-body-capture-type]
               [:update-body-capture -> *self* ! :update-body-capture]
               [:remove-body-capture -> *self* ! :remove-body-capture]
               [:update-req-name -> *self* ! :update-req-name]]])
    :opts
    {:ctx {}
     :actions
     {:reset-params
      (xs/assign-ctx-from-evt {:evt-prop :params
                               :ctx-prop :params
                               :static-ctx {:package {:reqs []}
                                            :error nil}})
      :set-default-package
      (xs/assign-ctx {:ctx-prop :package
                      :static-ctx {:reqs []}})
      :add-req
      (xs/xform-ctx
       {:ctx-prop :package}
       update :reqs conj {:req-name "" :captures {:headers {}}})
      :update-req-name
      (xs/xform-ctx-from-event
       {:ctx-prop :package}
       (fn [package {:keys [req-idx req-name]}]
         (assoc-in package [:reqs req-idx :req-name] req-name)))
      :update-header-capture
      (xs/xform-ctx-from-event
       {:ctx-prop :package}
       (fn [package {:keys [req-idx header template-var]}]
         (assoc-in package [:reqs req-idx :captures :headers header :template-var] template-var)))
      :remove-header-capture
      (xs/xform-ctx-from-event
       {:ctx-prop :package}
       (fn [package {:keys [req-idx header]}]
         (update-in package [:reqs req-idx :captures :headers] dissoc header)))
      :remove-all-body-captures
      (xs/xform-ctx-from-event
       {:ctx-prop :package}
       (fn [package {:keys [req-idx]}]
         (update-in package [:reqs req-idx :captures] dissoc :body)))
      :update-body-capture-type
      (xs/xform-ctx-from-event
       {:ctx-prop :package}
       (fn [package {:keys [req-idx body-capture-type]}]
         (assoc-in package [:reqs req-idx :captures :body] {:type body-capture-type :captures {}})))
      :update-body-capture
      (xs/xform-ctx-from-event
       {:ctx-prop :package}
       (fn [package {:keys [req-idx body-capture-key template-var]}]
         (assoc-in package [:reqs req-idx :captures :body :captures body-capture-key :template-var] template-var)))
      :remove-body-capture
      (xs/xform-ctx-from-event
       {:ctx-prop :package}
       (fn [package {:keys [req-idx body-capture-key]}]
         (update-in package [:reqs req-idx :captures :body :captures] dissoc body-capture-key)))}}}))

(defn svc
  ([]
   (svc nil))
  ([opts]
   (xs/interpret-and-start machine opts)))
