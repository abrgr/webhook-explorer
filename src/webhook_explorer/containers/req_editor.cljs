(ns webhook-explorer.containers.req-editor
  (:require [clojure.string :as string]
            [reagent.core :as r]
            [webhook-explorer.app-state :as app-state]
            [webhook-explorer.actions.reqs :as reqs-actions]
            [webhook-explorer.components.req-parts :as req-parts]
            [webhook-explorer.styles :as styles]
            [webhook-explorer.components.req-editor :as re]
            [goog.object :as obj]
            ["@material-ui/core/Typography" :default Typography]
            ["@material-ui/core/CircularProgress" :default CircularProgress]
            ["@material-ui/core/Snackbar" :default Snackbar]
            ["@material-ui/core/Button" :default Button]
            ["@material-ui/core/Dialog" :default Dialog]
            ["@material-ui/core/DialogActions" :default DialogActions]
            ["@material-ui/core/DialogContent" :default DialogContent]
            ["@material-ui/core/DialogContentText" :default DialogContentText]
            ["@material-ui/core/DialogTitle" :default DialogTitle]
            ["@material-ui/core/Tooltip" :default Tooltip]))

(def ^:private styled
  (styles/style-wrapper
   (fn [theme]
     {:success {:color "#4caf50"}
      :centered-container {:display "flex"
                           :alignItems "center"
                           :justifyContent "center"
                           :width "100%"}})))

(defn- success-status [status]
  (cond
    (< status 100) false
    (< status 400) true
    :else false))

(def ^:private update-path-by-key
  {:protocol [:item :detail :protocol]
   :method [:item :method]
   :host [:item :details :host]
   :path [:item :path]
   :qs [:item :details :qs]
   :headers [:item :details :req :headers]
   :body [:item :details :req :body]
   :res [:item :details :res]})

(defn- styled-dialog [{:keys [styles]}]
  (let [{{:keys [item in-progress]} :selected-item} @app-state/reqs
        {:keys [method path status]} item
        host (get-in item (-> update-path-by-key :host rest))
        protocol (get-in item (-> update-path-by-key :protocol rest))
        qs (get-in item (-> update-path-by-key :qs rest))
        headers (get-in item (-> update-path-by-key :headers rest))
        body (get-in item (-> update-path-by-key :body rest))
        res (get-in item (-> update-path-by-key :res rest))
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
     (if open
       [:<>
        [:> DialogTitle "Send Request"]
        [:> DialogContent
         [:> DialogContentText "Execute request or copy as curl"]
         [re/component
          {:protocol protocol
           :method method
           :host host
           :qs qs
           :headers non-host-headers
           :body body
           :on-update (fn [k v] (reqs-actions/update-selected-item-in (k update-path-by-key) v))}]
         (if in-progress
           [:div {:className (obj/get styles "centered-container")}
            [:> CircularProgress]]
           (when (and (some? status) (some? res))
             [:<>
              (let [success (success-status status)]
                [:> Typography
                 (merge
                  {:variant "h4"
                   :gutterBottom true
                   :classes (when success #js {:root (obj/get styles "success")})}
                  (when-not success {:color "error"}))
                 (str "Response: " status)])
              (when (zero? status)
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
               (req-parts/make-bodies
                {:raw {:label "Raw" :body (:body res)}})
               (:headers res)]]))]
        [:> DialogActions
         [:> Button {:onClick reqs-actions/send-selected-as-remote-request
                     :color "primary"}
          "Send request from server"]
         [:> Tooltip {:title "Change host to 'localhost'"
                      :disableHoverListener allow-local-req
                      :placement "top"}
          [:span
           [:> Button {:onClick reqs-actions/send-selected-as-local-request
                       :color "primary"
                       :disabled (not allow-local-req)}
            "Send request from browser"]]]
         [:> Button {:onClick reqs-actions/copy-selected-as-curl
                     :color "primary"}
          "Copy as curl"]
         [:> Button {:onClick on-close
                     :color "secondary"}
          "Cancel"]]]
       [:div])]))

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
