(ns webhook-explorer.state-machines.edit-package
  (:require [webhook-explorer.xstate :as xs]
            [webhook-explorer.env :as env]
            [webhook-explorer.remote.packages :as remote-pkgs]
            [goog.object :as obj]))

(def machine
  (xs/machine
   {:cfg
    (xs/cfg->machine
     :edit-package
     '[* [[:reset -> :start ! :reset-params]]
       > :idle []
       :start [[*transient* -> :ready ! :set-default-package]]
       :ready [[:update-package-name -> *self* ! :update-package-name]
               [:add-input-template-var -> *self* ! :add-input-template-var]
               [:remove-input-template-var -> *self* ! :remove-input-template-var]
               [:add-req -> *self* ! :add-req]
               [:remove-req -> *self* ! :remove-req]
               [:update-header-capture -> *self* ! :update-header-capture]
               [:remove-header-capture -> *self* ! :remove-header-capture]
               [:remove-all-body-captures -> *self* ! :remove-all-body-captures]
               [:update-body-capture-type -> *self* ! :update-body-capture-type]
               [:update-body-capture -> *self* ! :update-body-capture]
               [:remove-body-capture -> *self* ! :remove-body-capture]
               [:update-req-name -> *self* ! :update-req-name]
               [:update-req -> *self* ! :update-req]
               [:update-status-capture -> *self* ! :update-status-capture]
               [:save -> :.saving]
               (children
                > :editing [[after 3000 -> *self* ! :clear-notification]]
                :saving [[$ :save-package
                          :on-done -> :editing ! :show-saving-success
                          :on-error -> :editing ! :show-saving-error]])]])
    :opts
    {:ctx {}
     :actions
     {:reset-params
      (xs/assign-ctx-from-evt {:evt-prop :params
                               :ctx-prop :params
                               :static-ctx {:package {:name ""
                                                      :input-template-vars []
                                                      :reqs []}
                                            :error nil}})
      :set-default-package
      (xs/assign-ctx {:ctx-prop :package
                      :static-ctx {:name ""
                                   :input-template-vars []
                                   :reqs []}})
      :update-package-name
      (xs/xform-ctx-from-event
       {:ctx-prop :package}
       (fn [package {:keys [package-name]}]
         (assoc package :name package-name)))
      :add-req
      (xs/xform-ctx
       {:ctx-prop :package}
       update :reqs conj {:req-name ""
                          :captures {:headers {}}
                          :req {:qs {} :headers {} :body ""}})
      :remove-req
      (xs/xform-ctx-from-event
       {:ctx-prop :package}
       (fn [package {:keys [req-idx]}]
         (update
          package
          :reqs
          #(->> (concat (subvec % 0 req-idx)
                        (subvec % (inc req-idx)))
                (into [])))))
      :add-input-template-var
      (xs/xform-ctx-from-event
       {:ctx-prop :package}
       (fn [package {:keys [template-var]}]
         (update
          package
          :input-template-vars
          conj
          template-var)))
      :remove-input-template-var
      (xs/xform-ctx-from-event
       {:ctx-prop :package}
       (fn [package {:keys [template-var]}]
         (update
          package
          :input-template-vars
          (partial filterv (partial not= template-var)))))
      :update-req-name
      (xs/xform-ctx-from-event
       {:ctx-prop :package}
       (fn [package {:keys [req-idx req-name]}]
         (assoc-in package [:reqs req-idx :req-name] req-name)))
      :update-req
      (xs/xform-ctx-from-event
       {:ctx-prop :package}
       (fn [package {:keys [req-idx k v]}]
         (assoc-in package [:reqs req-idx :req k] v)))
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
         (update-in package [:reqs req-idx :captures :body :captures] dissoc body-capture-key)))
      :update-status-capture
      (xs/xform-ctx-from-event
       {:ctx-prop :package}
       (fn [package {:keys [req-idx template-var]}]
         (assoc-in package [:reqs req-idx :captures :status :template-var] template-var)))
      :show-saving-error
      (xs/assign-ctx {:ctx-prop :notification
                      :static-ctx "Error saving package"})
      :show-saving-success
      (xs/assign-ctx {:ctx-prop :notification
                      :static-ctx "Successfully saved package"})
      :clear-notification
      (xs/assign-ctx {:ctx-prop :notification
                      :static-ctx nil})}
     :services
     {:save-package (fn [{:keys [package] :as ctx} evt]
                      (remote-pkgs/save-package package))}}}))

(defn svc
  ([]
   (svc nil))
  ([opts]
   (xs/interpret-and-start machine opts)))
