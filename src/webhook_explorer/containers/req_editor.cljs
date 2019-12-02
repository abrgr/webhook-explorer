(ns webhook-explorer.containers.req-editor
  (:require [clojure.core.async :as async]
            [webhook-explorer.app-state :as app-state]
            [webhook-explorer.actions.reqs :as reqs-actions]
            ["@material-ui/core/Button" :default Button]
            ["@material-ui/core/Dialog" :default Dialog]
            ["@material-ui/core/DialogActions" :default DialogActions]
            ["@material-ui/core/DialogContent" :default DialogContent]
            ["@material-ui/core/DialogContentText" :default DialogContentText]
            ["@material-ui/core/DialogTitle" :default DialogTitle]))

(defn component []
  (let [{:keys [selected-item]} @app-state/reqs
        open (some? selected-item)
        on-close reqs-actions/unselect-item]
    [:> Dialog {:open open
                :onClose on-close}
      [:> DialogTitle "cURL"]
      [:> DialogContent
        [:> DialogContentText "Generate a cURL command"]
        [:div "My stuff"]
        [:> DialogActions
          [:> Button {:onClick (fn []) :color "primary"} "Copy"]
          [:> Button {:onClick on-close :color "secondary"} "Cancel"]]]]))
