(ns webhook-explorer.containers.home
  (:require [reagent.core :as r]
            [goog.object :as obj]
            [webhook-explorer.app-state :as app-state]
            [webhook-explorer.styles :as styles]
            [webhook-explorer.actions.reqs :as reqs-actions]
            [webhook-explorer.init :as init]
            ["codemirror" :as CM]
            ["react-codemirror" :as CodeMirror]
            ["codemirror/mode/meta"]
            ["codemirror/mode/javascript/javascript"]
            ["codemirror/mode/xml/xml"]
            ["codemirror/mode/clojure/clojure"]
            ["codemirror/mode/yaml/yaml"]
            ["@material-ui/core/Avatar" :default Avatar]
            ["@material-ui/core/Button" :default Button]
            ["@material-ui/core/Card" :default Card]
            ["@material-ui/core/CardActions" :default CardActions]
            ["@material-ui/core/CardContent" :default CardContent]
            ["@material-ui/core/CardHeader" :default CardHeader]
            ["@material-ui/core/Collapse" :default Collapse]
            ["@material-ui/core/ExpansionPanel" :default ExpansionPanel]
            ["@material-ui/core/ExpansionPanelDetails" :default ExpansionPanelDetails]
            ["@material-ui/core/ExpansionPanelSummary" :default ExpansionPanelSummary]
            ["@material-ui/core/IconButton" :default IconButton]
            ["@material-ui/core/Tooltip" :default Tooltip]
            ["@material-ui/core/Table" :default Table]
            ["@material-ui/core/TableBody" :default TableBody]
            ["@material-ui/core/TableCell" :default TableCell]
            ["@material-ui/core/TableHead" :default TableHead]
            ["@material-ui/core/TableRow" :default TableRow]
            ["@material-ui/core/Typography" :default Typography]
            ["@material-ui/icons/ExpandMore" :default ExpandMoreIcon]
            ["@material-ui/icons/Favorite" :default FavoriteIcon]
            ["@material-ui/icons/Folder" :default FolderIcon]
            ["@material-ui/icons/Share" :default ShareIcon]
            ["@material-ui/icons/PlaylistAdd" :default AddToCollectionIcon]))

(defn- init! []
  (styles/inject-css-link "https://cdnjs.cloudflare.com/ajax/libs/codemirror/5.48.4/codemirror.css"))

(init/register-init 10 init!)

(def ^:private styled
  (styles/style-wrapper
    (fn [theme]
      {:card {:width "80%"
              :minWidth "480px"
              :maxWidth "768px"
              :margin "25px auto"}
       :card-action-btn {:margin (.spacing theme 1)}
       :date {:fontSize 14}
       :method {:width 60
                :height 60
                :margin 10}
       :fix-card-content {:marginBottom "-24px"}
       :hidden {:display "none"}
       :code {:width "100%"
              "& > .CodeMirror" {:height "auto"
                                 :border "1px solid #eee"}}
       :blurred {:position "relative"
                 "&:after" {:content "\" \""
                            :position "absolute"
                            :z-index 1
                            :bottom 0
                            :left 0
                            :pointer-events "none"
                            :background-image "linear-gradient(to bottom, rgba(255,255,255, 0), rgba(255,255,255, 1) 90%)"
                            :width "100%"
                            :height "4em"}}
       :previewed-card-content {:margin-top "-32px"}})))

(defn- action-btn
  ([label icon]
    (action-btn label icon nil))
  ([label icon icon-props]
    [:> Tooltip {:title label :aria-label label}
      [:> IconButton {:aria-label label}
        [:> icon icon-props]]]))

(defn- editor [value content-type styles]
  (let [mode (when-let [m (.findModeByMIME CM (or content-type "text/plain"))]
               (obj/get m "mode"))]
    [:> CodeMirror {:className (obj/get styles "code")
                    :value value
                    :options #js {:viewportMargin ##Inf
                                  :mode mode}}]))

(defn- headers-view [title headers]
  [:> ExpansionPanel {:elevation 0}
    [:> ExpansionPanelSummary {:expandIcon (r/as-element [:> ExpandMoreIcon])}
      title]
    [:> ExpansionPanelDetails
      [:<>
        [:> Table {:aria-label "headers"}
          [:> TableHead
            [:> TableRow
              [:> TableCell "Header"]
              [:> TableCell "Value"]]]
          [:> TableBody
            (for [[header value] headers]
              ^{:key header}
              [:> TableRow
                [:> TableCell header]
                [:> TableCell value]])]]]]])

(defn- body-view [title body content-type styles]
  [:> ExpansionPanel {:elevation 0}
    [:> ExpansionPanelSummary {:expandIcon (r/as-element [:> ExpandMoreIcon])}
      title]
    [:> ExpansionPanelDetails
      [editor body content-type styles]]])

(defn- req-card [{{:keys [id date path method req-headers req-body res-headers res-body]} :item :keys [styles favorited]}]
  [:> Card {:className (obj/get styles "card")}
    [:> CardHeader
      {:avatar (r/as-element
                  [:> Avatar {:aria-label method
                              :className (obj/get styles "method")}
                    method])
       :action (r/as-element [:div
                               [action-btn "Favorite" FavoriteIcon (when favorited {:color "secondary"})]
                               [action-btn "Save to folder" FolderIcon]
                               [action-btn "Add to request collection" AddToCollectionIcon]
                               [action-btn "Share" ShareIcon]])
       :title path
       :subheader date}]
    [:> CardContent {:className (obj/get styles "fix-card-content")}
      [headers-view "Request Headers" req-headers]
      [body-view "Request Body" req-body (get req-headers "Content-Type") styles]
      [headers-view "Response Headers" res-headers]
      [body-view "Response Body" res-body (get res-headers "Content-Type") styles]]
    [:> CardActions
      [:> Button {:className (obj/get styles "card-action-btn")
                  :color "primary"}
        "Copy as cURL"]
      [:> Button {:className (obj/get styles "card-action-btn")
                  :color "primary"}
        "Run local"]]])

(defn- -component [{:keys [styles]}]
  (let [reqs-state @app-state/reqs]
    [:div
       (for [{:keys [id] :as item} (:items reqs-state)]
         ^{:key (:id item)}
         [req-card {:item item
                    :styles styles
                    :favorited (-> reqs-state :favorite-reqs (contains? id))}])]))

(defn component []
  [styled {} -component])
