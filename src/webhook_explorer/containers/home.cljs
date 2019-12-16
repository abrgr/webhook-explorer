(ns webhook-explorer.containers.home
  (:require [clojure.core.async :as async]
            [reagent.core :as r]
            [goog.object :as obj]
            [webhook-explorer.app-state :as app-state]
            [webhook-explorer.containers.req-editor :as req-editor]
            [webhook-explorer.components.req-parts :as req-parts]
            [webhook-explorer.styles :as styles]
            [webhook-explorer.actions.reqs :as reqs-actions]
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
            ["@material-ui/core/IconButton" :default IconButton]
            ["@material-ui/core/CircularProgress" :default CircularProgress]
            ["@material-ui/core/Tooltip" :default Tooltip]
            ["@material-ui/core/Typography" :default Typography]
            ["@material-ui/core/Fab" :default FloatingActionButton]
            ["@material-ui/core/styles" :refer [withTheme] :rename {withTheme with-theme}]
            ["@material-ui/icons/Send" :default SendIcon]
            ["@material-ui/icons/Favorite" :default FavoriteIcon]
            ["@material-ui/icons/Folder" :default FolderIcon]
            ["@material-ui/icons/Share" :default ShareIcon]
            ["@material-ui/icons/PlaylistAdd" :default AddToCollectionIcon]))

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
       :previewed-card-content {:margin-top "-32px"}
       :send-btn {:margin-right 15
                  :margin-bottom 15
                  :margin-left "auto"}})))

(defn- action-btn
  ([label icon]
    (action-btn label icon nil))
  ([label icon icon-props]
    [:> Tooltip {:title label :aria-label label}
      [:> IconButton {:aria-label label}
        [:> icon icon-props]]]))

(defn- req-card
  [{:keys [styles favorited]
   {:keys [id
           date
           host
           path
           method]
     {{req-headers :headers
        req-body :body} :req
       {res-headers :headers
        res-body :body} :res} :details
     :as item} :item}
    on-visibility-toggled]
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
       :title (str host path)
       :subheader date}]
    [:> CardContent {:className (obj/get styles "fix-card-content")}
      [req-parts/headers-view "Request Headers" req-headers on-visibility-toggled]
      [req-parts/body-view "Request Body" req-body req-headers on-visibility-toggled]
      [req-parts/headers-view "Response Headers" res-headers on-visibility-toggled]
      [req-parts/body-view "Response Body" res-body res-headers on-visibility-toggled]]
    [:> CardActions
      [:> FloatingActionButton {:color "primary"
                                :className (obj/get styles "send-btn")
                                :aria-label "execute"
                                :onClick #(reqs-actions/select-item item)}
        [:> SendIcon]]]])

(def ^:private cell-measure-cache
  (CellMeasurerCache. #js {:defaultHeight 431
                           :fixedWidth true}))

(defn- row-renderer [styles theme on-row-updated props]
  (r/as-element
    (let [{:keys [items favorite-reqs next-req]} @app-state/reqs
          item-count (count items)
          idx (obj/get props "index")
          {:keys [id details] :as item} (get items idx)
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
                      (when (pos? ms-remaining) (js/requestAnimationFrame (partial advance-animation (- ms-remaining 16))))) ; TODO: set deadline instead of assuming 16ms/frame
                    (start-animating []
                      (advance-animation (obj/getValueByKeys theme #js ["transitions" "duration" "standard"])))
                    (load-details []
                      (start-animating)
                      (async/go
                        (async/<! (reqs-actions/load-full-req item))
                        (on-row-updated idx)
                        (start-animating)))]
              (r/as-element
                [:div {:style style
                       :className (obj/get styles "card-container")
                       :on-load measure}
                  (cond
                    (and (= idx item-count) (some? next-req)) [:> CircularProgress]
                    (>= idx item-count) [:div {:style {:height 50}} " "]
                    :else [req-card
                            {:item item
                             :styles styles
                             :favorited (contains? favorite-reqs id)}
                            (if (some? details)
                              start-animating
                              load-details)])]))))])))

(defn- load-more-rows []
  (.then (reqs-actions/load-next-items)
    #(when (not= % :stop) (.clearAll cell-measure-cache))))

(defn- req-list []
  (let [list-ref (r/atom nil)]
    (fn [{:keys [size styles theme]}]
      (let [{:keys [items next-req] :as reqs-state} @app-state/reqs]
        [:> InfiniteLoader {:isRowLoaded #(or (nil? next-req) (< (obj/get % "index") (count items)))
                            :loadMoreRows load-more-rows
                            :rowCount (if (nil? next-req) (count items) (inc (count items)))
                            :height (obj/get size "height")}
          (fn [scroll-info]
            (r/as-element
              [:> List {:ref #(do (reset! list-ref %)
                                  ((obj/get scroll-info "registerChild") %))
                        :onRowsRendered (obj/get scroll-info "onRowsRendered")
                        :rowRenderer (partial row-renderer styles theme #(when-not (nil? @list-ref) (.recomputeGridSize @list-ref %)))
                        :height (obj/get size "height")
                        :width (obj/get size "width")
                        :rowCount (if (nil? next-req) (count items) (inc (count items)))
                        :rowHeight (obj/get cell-measure-cache "rowHeight")
                        :deferredMeasurementCache cell-measure-cache}]))]))))

(defn- -component [props]
  (let [styles (obj/get props "styles")
        theme (obj/get props "theme")]
    (r/as-element
      [:<>
        [req-editor/component]
        [:div {:className (obj/get styles "container")}
          [:> AutoSizer
            (fn [size]
              (r/as-element
                [req-list {:size size
                           :styles styles
                           :theme theme}]))]]])))

(def ^:private -component-with-styles-and-theme (with-theme -component))

(defn -component-with-styles [{:keys [styles]}]
  [:> -component-with-styles-and-theme {:styles styles}])

(defn component []
  [styled {} -component-with-styles])
