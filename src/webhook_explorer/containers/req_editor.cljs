(ns webhook-explorer.containers.req-editor
  (:require [clojure.core.async :as async]
            [webhook-explorer.app-state :as app-state]
            [webhook-explorer.actions.reqs :as reqs-actions]
            [webhook-explorer.components.req-parts :as req-parts]
            [goog.object :as obj]
            ["@material-ui/core/Button" :default Button]
            ["@material-ui/core/Dialog" :default Dialog]
            ["@material-ui/core/DialogActions" :default DialogActions]
            ["@material-ui/core/DialogContent" :default DialogContent]
            ["@material-ui/core/DialogContentText" :default DialogContentText]
            ["@material-ui/core/DialogTitle" :default DialogTitle]
            ["@material-ui/core/TextField" :default TextField]))

(defn component []
  (let [{{:keys [type item]} :selected-item} @app-state/reqs
        headers (get-in item [:details :req :headers])
        body (get-in item [:details :req :body])
        non-host-headers (->> headers (filter (comp not #{:Host} first)) (into {}))
        open (some? type)
        on-close reqs-actions/unselect-item
        {:keys [title desc action-name action]}
        (case type
          :curl {:title "cURL"
                 :desc "Generate a cURL command"
                 :action-name "Copy"
                 :action (fn []
                           (let [{{:keys [type item]} :selected-item} @app-state/reqs]
                             (println item)))}
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
        [:> TextField {:fullWidth true
                       :label "Host"
                       :value (get headers :Host)
                       :onChange #(reqs-actions/update-selected-item-in [:details :req :headers :Host] (obj/getValueByKeys % #js ["target" "value"]))}]
        [req-parts/editable-headers-view "Request Headers" non-host-headers #(reqs-actions/update-selected-item-in [:details :req :headers %1] %2)]
        [req-parts/editable-body-view "Request Body" body headers #(reqs-actions/update-selected-item-in [:details :req :body] %)]]
      [:> DialogActions
        [:> Button {:onClick action :color "primary"} action-name]
        [:> Button {:onClick on-close :color "secondary"} "Cancel"]]]))
