(ns webhook-explorer.containers.home
  (:require [reagent.core :as r]
            [goog.object :as obj]
            [webhook-explorer.app-state :as app-state]
            [webhook-explorer.styles :as styles]
            [webhook-explorer.actions.reqs :as reqs-actions]
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

(defn- req-card [{{:keys [id date path method headers]} :item :keys [styles favorited]}]
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
      [:> ExpansionPanel {:elevation 0}
        [:> ExpansionPanelSummary {:expandIcon (r/as-element [:> ExpandMoreIcon])}
          "Request Headers"]
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
                    [:> TableCell value]])]]]]]
      [:> ExpansionPanel {:elevation 0}
        [:> ExpansionPanelSummary {:expandIcon (r/as-element [:> ExpandMoreIcon])}
          "Request Body"]
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
                    [:> TableCell value]])]]]]]]
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
