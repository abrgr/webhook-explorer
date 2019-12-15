(ns webhook-explorer.containers.req-editor
  (:require [clojure.core.async :as async]
            [clojure.string :as string]
            [reagent.core :as r]
            [webhook-explorer.app-state :as app-state]
            [webhook-explorer.actions.reqs :as reqs-actions]
            [webhook-explorer.components.req-parts :as req-parts]
            [goog.object :as obj]
            ["@material-ui/core/Snackbar" :default Snackbar]
            ["@material-ui/core/InputLabel" :default InputLabel]
            ["@material-ui/core/MenuItem" :default MenuItem]
            ["@material-ui/core/FormControlLabel" :default FormControlLabel]
            ["@material-ui/core/FormControl" :default FormControl]
            ["@material-ui/core/Select" :default Select]
            ["@material-ui/core/Switch" :default Switch]
            ["@material-ui/core/Button" :default Button]
            ["@material-ui/core/Dialog" :default Dialog]
            ["@material-ui/core/DialogActions" :default DialogActions]
            ["@material-ui/core/DialogContent" :default DialogContent]
            ["@material-ui/core/DialogContentText" :default DialogContentText]
            ["@material-ui/core/DialogTitle" :default DialogTitle]
            ["@material-ui/core/TextField" :default TextField]
            ["@material-ui/core/Tooltip" :default Tooltip]))

(defn- dialog []
  (let [{{:keys [item]} :selected-item} @app-state/reqs
        {:keys [method path is-secure]} item
        headers (get-in item [:details :req :headers])
        body (get-in item [:details :req :body])
        non-host-headers (->> headers (filter (comp not #{:Host} first)) (into {}))
        host (get headers :Host)
        open (some? item)
        on-close reqs-actions/unselect-item
        allow-local-req (and (some? host)
                             (or (string/starts-with? host "localhost")
                               (string/starts-with? host "127.0.0.1")))]
    [:> Dialog {:open open
                :onClose on-close
                :fullWidth true
                :PaperProps #js {:style #js {"height" "75%"}}}
      [:> DialogTitle "Send Request"]
      [:> DialogContent
        [:> DialogContentText "Execute locally from the browser or copy as curl"]
        [:> FormControl {:fullWidth true
                         :margin "normal"}
          [:> FormControlLabel {:label "Secure"
                                :control (r/as-element [:> Switch {:checked is-secure :onChange #(reqs-actions/update-selected-item-in [:item :is-secure] (obj/getValueByKeys % #js ["target" "checked"]))}])}]]
        [:> FormControl {:fullWidth true
                         :margin "normal"}
          [:> InputLabel "Method"]
          [:> Select {:value method
                      :onChange #(reqs-actions/update-selected-item-in [:item :method] (obj/getValueByKeys % #js ["target" "value"]))}
            [:> MenuItem {:value "GET"} "GET"]
            [:> MenuItem {:value "POST"} "POST"]
            [:> MenuItem {:value "PUT"} "PUT"]
            [:> MenuItem {:value "PATCH"} "PATCH"]
            [:> MenuItem {:value "DELETE"} "DELETE"]
            [:> MenuItem {:value "OPTIONS"} "OPTIONS"]]]
        [:> FormControl {:fullWidth true
                         :margin "normal"}
          [:> TextField {:fullWidth true
                         :label "Host"
                         :value host
                         :onChange #(reqs-actions/update-selected-item-in [:item :details :req :headers :Host] (obj/getValueByKeys % #js ["target" "value"]))}]]
        [:> FormControl {:fullWidth true
                         :margin "normal"}
          [:> TextField {:fullWidth true
                         :label "Path"
                         :value path
                         :onChange #(reqs-actions/update-selected-item-in [:item :path] (obj/getValueByKeys % #js ["target" "value"]))}]]
        [req-parts/editable-headers-view "Request Headers" non-host-headers #(reqs-actions/update-selected-item-in [:item :details :req :headers %1] %2)]
        [req-parts/editable-body-view "Request Body" body headers #(reqs-actions/update-selected-item-in [:item :details :req :body] %)]]
      [:> DialogActions
        [:> Tooltip {:title "Change host to 'localhost'"
                     :disableHoverListener allow-local-req
                     :arrow true
                     :placement "top"}
          [:span
            [:> Button {:onClick reqs-actions/send-selected-as-local-request
                        :color "primary"
                        :disabled (not allow-local-req)}
                      "Send local request"]]]
        [:> Button {:onClick reqs-actions/copy-selected-as-curl
                    :color "primary"}
                  "Copy as curl"]
        [:> Button {:onClick on-close
                    :color "secondary"}
                  "Cancel"]]]))

(defn- notification []
  (let [{{:keys [notification]} :selected-item} @app-state/reqs
        open (some? notification)]
    [:> Snackbar
      {:open open
       :autoHideDuration 3000
       :onClose #(reqs-actions/update-selected-item-in [:notification] nil)
       :message (r/as-element [:span notification])}]))

(defn component []
  [:<>
    [notification]
    [dialog]])
