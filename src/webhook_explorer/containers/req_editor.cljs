(ns webhook-explorer.containers.req-editor
  (:require [clojure.core.async :as async]
            [webhook-explorer.app-state :as app-state]
            [webhook-explorer.actions.reqs :as reqs-actions]
            [webhook-explorer.components.req-parts :as req-parts]
            ["@material-ui/core/Button" :default Button]
            ["@material-ui/core/Dialog" :default Dialog]
            ["@material-ui/core/DialogActions" :default DialogActions]
            ["@material-ui/core/DialogContent" :default DialogContent]
            ["@material-ui/core/DialogContentText" :default DialogContentText]
            ["@material-ui/core/DialogTitle" :default DialogTitle]))

(defn component []
  (let [{{:keys [type item]} :selected-item} @app-state/reqs
        headers (get-in item [:details :req :headers])
        body (get-in item [:details :req :body])
        open (some? type)
        on-close reqs-actions/unselect-item
        {:keys [title desc action-name action]}
        (case type
          :curl {:title "cURL"
                 :desc "Generate a cURL command"
                 :action-name "Copy"
                 :action (fn [])}
          :local {:title "Execute request"
                  :desc "Execute request from the browser"
                  :action-name "Execute"
                  :action (fn [])}
          {:title "" :desc "" :action-name "" :action (fn [])})]
    [:> Dialog {:open open
                :onClose on-close
                :fullWidth true
                :PaperProps #js {:style #js {"height" "75%"}}}
      [:> DialogTitle title]
      [:> DialogContent
        [:> DialogContentText desc]
        [req-parts/headers-view "Request Headers" headers]
        [req-parts/body-view "Request Body" body headers]]
      [:> DialogActions
        [:> Button {:onClick action :color "primary"} action-name]
        [:> Button {:onClick on-close :color "secondary"} "Cancel"]]]))
