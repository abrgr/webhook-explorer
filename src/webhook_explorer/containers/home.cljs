(ns webhook-explorer.containers.home
  (:require [reagent.core :as r]
            [goog.object :as obj]
            [webhook-explorer.app-state :as app-state]
            [webhook-explorer.styles :as styles]
            [webhook-explorer.actions.reqs :as reqs-actions]
            ["@material-ui/core/Avatar" :default Avatar]
            ["@material-ui/core/Card" :default Card]
            ["@material-ui/core/CardActions" :default CardActions]
            ["@material-ui/core/CardContent" :default CardContent]
            ["@material-ui/core/CardHeader" :default CardHeader]
            ["@material-ui/core/Collapse" :default Collapse]
            ["@material-ui/core/IconButton" :default IconButton]
            ["@material-ui/core/Typography" :default Typography]
            ["@material-ui/icons/Favorite" :default FavoriteIcon]
            ["@material-ui/icons/Folder" :default FolderIcon]
            ["@material-ui/icons/Share" :default ShareIcon]
            ["@material-ui/icons/ExpandMore" :default ExpandMoreIcon]))

(def ^:private styled
  (styles/style-wrapper
    (fn [theme]
      {:card {:width "80%"
              :minWidth "480px"
              :maxWidth "768px"
              :margin "25px auto"}
       :date {:fontSize 14}
       :method {:width 60
                :height 60
                :margin 10}
       :expand {:transform "rotate(0deg)"
                :marginLeft "auto"
                :transition (-> theme
                                (.-transitions)
                                (.create "transform", #js {:duration (obj/getValueByKeys theme #js ["transitions" "duration" "shortest"])}))}
       :expandOpen {:transform "rotate(180deg)"}})))

(defn- req-card [{{:keys [id date path method]} :item :keys [styles expanded]}]
  [:> Card {:className (.-card styles)}
    [:> CardHeader
      {:avatar (r/as-element
                  [:> Avatar {:aria-label method
                              :className (.-method styles)}
                    method])
       :action (r/as-element [:div
                               [:> IconButton {:aria-label "Favorite"}
                                 [:> FavoriteIcon]]
                               [:> IconButton {:aria-label "Save to folder"}
                                 [:> FolderIcon]]
                               [:> IconButton {:aria-label "Share"}
                                 [:> ShareIcon]]])
       :title path
       :subheader date}]
    [:> CardActions {:disableSpacing true}
      [:> IconButton {:aria-label "View request"
                      :aria-expanded expanded
                      :on-click (partial reqs-actions/toggle-expand id)
                      :className (str (.-expand styles) " " (if expanded (.-expandOpen styles) ""))}
        [:> ExpandMoreIcon]]]
    [:> Collapse {:in expanded
                  :timeout "auto"
                  :unmountOnExit true}
      [:> CardContent
        [:> Typography {:paragraph true}
          "Hello world"]]]])

(defn- -component [{:keys [styles]}]
  (let [reqs-state @app-state/reqs]
    [:div
       (for [{:keys [id] :as item} (:items reqs-state)]
         ^{:key (:id item)}
         [req-card {:item item
                    :styles styles
                    :expanded (-> reqs-state :expanded-reqs (contains? id))}])]))

(defn component []
  [styled {} -component])
