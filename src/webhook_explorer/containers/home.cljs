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
            ["react-virtualized/dist/commonjs/AutoSizer" :default AutoSizer]
            ["react-virtualized/dist/commonjs/CellMeasurer" :refer [CellMeasurer CellMeasurerCache]]
            ["react-virtualized/dist/commonjs/List" :default List]
            ["react-virtualized/dist/commonjs/InfiniteLoader" :default InfiniteLoader]
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
            ["@material-ui/core/CircularProgress" :default CircularProgress]
            ["@material-ui/core/Tooltip" :default Tooltip]
            ["@material-ui/core/Table" :default Table]
            ["@material-ui/core/TableBody" :default TableBody]
            ["@material-ui/core/TableCell" :default TableCell]
            ["@material-ui/core/TableHead" :default TableHead]
            ["@material-ui/core/TableRow" :default TableRow]
            ["@material-ui/core/Typography" :default Typography]
            ["@material-ui/core/styles" :refer [withTheme] :rename {withTheme with-theme}]
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
      {:container {:flex "1"}
       :card-container {:display "flex"
                        :justifyContent "center"}
       :card {:width "80%"
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

(defn- headers-view [title headers on-resize]
  [:> ExpansionPanel {:elevation 0 :TransitionProps #js {:onEntered on-resize :onExit on-resize}}
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

(defn- body-view [title body content-type styles on-resize]
  [:> ExpansionPanel {:elevation 0 :onChange on-resize}
    [:> ExpansionPanelSummary {:expandIcon (r/as-element [:> ExpandMoreIcon])}
      title]
    [:> ExpansionPanelDetails
      [editor body content-type styles]]])

(defn- req-card
  [{{:keys [id
            date
            path
            method
            req-headers
            req-body
            res-headers
            res-body]}
    :item :keys [styles favorited]}
    on-resize]
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
      [headers-view "Request Headers" req-headers on-resize]
      [body-view "Request Body" req-body (get req-headers "Content-Type") styles on-resize]
      [headers-view "Response Headers" res-headers on-resize]
      [body-view "Response Body" res-body (get res-headers "Content-Type") styles on-resize]]
    [:> CardActions
      [:> Button {:className (obj/get styles "card-action-btn")
                  :color "primary"}
        "Copy as cURL"]
      [:> Button {:className (obj/get styles "card-action-btn")
                  :color "primary"}
        "Run local"]]])

(def ^:private cell-measure-cache
  (CellMeasurerCache. #js {:defaultHeight 431
                           :fixedWidth true}))

(defn- row-renderer [styles theme props]
  (r/as-element
    (let [{:keys [items favorite-reqs]} @app-state/reqs
          idx (obj/get props "index")
          {:keys [id] :as item} (get items idx)
          key (obj/get props "key")
          style (obj/get props "style")
          parent (obj/get props "parent")]
      (obj/remove style "height")
      ^{:key key}
      [:> CellMeasurer {:cache cell-measure-cache
                        :columnIndex 0
                        :parent parent
                        :rowIndex idx
                        :style style}
        (fn [measurer]
          (let [measure (obj/get measurer "measure")]
            (letfn [(advance-animation [ms-remaining]
                      (measure)
                      (when (pos? ms-remaining) (js/setTimeout (partial advance-animation (- ms-remaining 16)) 16)))
                    (start-animating []
                      (advance-animation (obj/getValueByKeys theme #js ["transitions" "duration" "standard"])))]
              (r/as-element
                [:div {:style style
                       :className (obj/get styles "card-container")
                       :on-load measure}
                  (if (nil? item)
                    [:> CircularProgress]
                    [req-card
                      {:item item
                       :styles styles
                       :favorited (contains? favorite-reqs id)}
                      start-animating])]))))])))

(defn- load-more-rows []
  (js/Promise. (fn [resolve-promise] (resolve-promise))))

(defn- -component [props]
  (let [styles (obj/get props "styles")
        theme (obj/get props "theme")
        {:keys [items in-progress-req next-req favorite-reqs]} @app-state/reqs
        row-count (if (some? next-req) (inc (count items)) (count items))]
    (r/as-element
      [:div {:className (obj/get styles "container")}
        [:> AutoSizer
          (fn [size]
            (r/as-element
              [:> InfiniteLoader {:isRowLoaded #(or (nil? next-req) (< (obj/get % "index") (count items)))
                                  :loadMoreRows load-more-rows
                                  :rowCount row-count
                                  :height (obj/get size "height")}
                (fn [scroll-info]
                  (r/as-element
                    [:> List {:ref (obj/get scroll-info "registerChild")
                              :onRowsRendered (obj/get scroll-info "onRowsRendered")
                              :rowRenderer (partial row-renderer styles theme)
                              :height (obj/get size "height")
                              :width (obj/get size "width")
                              :rowCount row-count
                              :rowHeight (obj/get cell-measure-cache "rowHeight")
                              :deferredMeasurementCache cell-measure-cache}]))]))]])))

(def ^:private -component-with-styles-and-theme (with-theme -component))

(defn -component-with-styles [{:keys [styles]}]
  [:> -component-with-styles-and-theme {:styles styles}])

(defn component []
  [styled {} -component-with-styles])
