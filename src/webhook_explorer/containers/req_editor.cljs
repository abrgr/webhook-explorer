(ns webhook-explorer.containers.req-editor
  (:require [clojure.core.async :as async]
            [clojure.string :as string]
            [reagent.core :as r]
            [webhook-explorer.app-state :as app-state]
            [webhook-explorer.actions.reqs :as reqs-actions]
            [webhook-explorer.components.req-parts :as req-parts]
            [webhook-explorer.styles :as styles]
            [goog.object :as obj]
            ["@material-ui/core/Typography" :default Typography]
            ["@material-ui/core/CircularProgress" :default CircularProgress]
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

(def ^:private styled
  (styles/style-wrapper
    (fn [theme]
      {:success {:color "#4caf50"}
       :centered-container {:display "flex"
                            :alignItems "center"
                            :justifyContent "center"
                            :width "100%"}})))

(defn- styled-dialog [{:keys [styles]}]
  (let [{{:keys [item in-progress res]} :selected-item} @app-state/reqs
        {:keys [method path]} item
        host (get-in item [:details :host])
        protocol (get-in item [:details :protocol])
        qs (get-in item [:details :qs])
        headers (get-in item [:details :req :headers])
        body (get-in item [:details :req :body])
        non-host-headers (->> headers (filter (comp not #{:Host} first)) (into {}))
        open (some? item)
        on-close reqs-actions/unselect-item
        allow-local-req (and (some? host)
                             (or (string/starts-with? host "localhost")
                               (string/starts-with? host "127.0.0.1")))]
    [:> Dialog {:open open
                :onClose on-close
                :fullWidth true
                :PaperProps #js {:style #js {"height" "75%"}}}
      (when open
        [:<>
          [:> DialogTitle "Send Request"]
          [:> DialogContent
            [:> DialogContentText "Execute locally from the browser or copy as curl"]
            [:> FormControl {:fullWidth true
                             :margin "normal"}
              [:> FormControlLabel {:label "Secure"
                                    :control (r/as-element
                                               [:> Switch {:checked (= protocol "https")
                                                           :onChange #(reqs-actions/update-selected-item-in
                                                                        [:item :details :protocol]
                                                                        (if (obj/getValueByKeys % #js ["target" "checked"])
                                                                          "https"
                                                                          "http"))}])}]]
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
                             :onChange #(reqs-actions/update-selected-item-in [:item :details :host] (obj/getValueByKeys % #js ["target" "value"]))}]]
            [:> FormControl {:fullWidth true
                             :margin "normal"}
              [:> TextField {:fullWidth true
                             :label "Path"
                             :value path
                             :onChange #(reqs-actions/update-selected-item-in [:item :path] (obj/getValueByKeys % #js ["target" "value"]))}]]
            [req-parts/editable-qs-view
              "Query Params"
              qs
              #(if (nil? %2)
                 (reqs-actions/update-selected-item-in [:item :details :qs] (dissoc qs %1))
                 (reqs-actions/update-selected-item-in [:item :details :qs %1] %2))]
            [req-parts/editable-headers-view
              "Request Headers"
              non-host-headers
              #(if (nil? %2)
                 (reqs-actions/update-selected-item-in [:item :details :req :headers] (dissoc headers %1))
                 (reqs-actions/update-selected-item-in [:item :details :req :headers %1] %2))]
            [req-parts/editable-body-view
              "Request Body"
              body
              headers
              #(reqs-actions/update-selected-item-in [:item :details :req :body] %)]
            (if in-progress
              [:div {:className (obj/get styles "centered-container")}
                [:> CircularProgress]]
              (when res
                [:<>
                  [:> Typography {:variant "h4"
                                  :gutterBottom true
                                  :classes (when (:success res) #js {:root (obj/get styles "success")})
                                  :color (when-not (:success res) "error")}
                    (str "Response: " (:status res))]
                  (when (zero? (:status res))
                    [:<>
                      [:> Typography {:gutterBottom true}
                        (str
                          "It looks like you either experienced a CORS error or a network error. "
                          "Please first confirm that there is a server running on '" host "'. "
                          "Then, ensure that the server: 1) responds to OPTIONS requests with "
                          "the appropriate CORS headers (Access-Control-Allow-Origin, "
                          "Access-Control-Allow-Method, Access-Control-Allow-Headers, "
                          "Access-Control-Expose-Headers), likely set to '*', and 2) includes the same "
                          "headers when responding to " method " " path ".")]
                      (when-not allow-local-req
                        [:> Typography {:gutterBottom true}
                          (str
                            "Or, you can re-run the request via a server-side proxy instead of "
                            "your browser to avoid any CORS issues.")])])
                  [req-parts/headers-view
                    "Response Headers"
                    (:headers res)]
                  [req-parts/body-view
                    "Response Body"
                    (:body res)
                    (:headers res)]]))]
          [:> DialogActions
            [:> Tooltip {:title "Change host to 'localhost'"
                         :disableHoverListener allow-local-req
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
                      "Cancel"]]])]))

(defn- dialog []
  [styled {} styled-dialog])

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
