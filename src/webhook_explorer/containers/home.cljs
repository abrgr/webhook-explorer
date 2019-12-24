(ns webhook-explorer.containers.home
  (:require [clojure.core.async :as async]
            [clojure.string :as string]
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
            ["@material-ui/core/Paper" :default Paper]
            ["@material-ui/core/TextField" :default TextField]
            ["@material-ui/core/ListSubheader" :default ListSubheader]
            ["@material-ui/core/ListItemIcon" :default ListItemIcon]
            ["@material-ui/core/ListItemText" :default ListItemText]
            ["@material-ui/core/styles" :refer [withTheme] :rename {withTheme with-theme}]
            ["@material-ui/core/Menu" :default Menu]
            ["@material-ui/core/MenuItem" :default MenuItem]
            ["@material-ui/icons/Add" :default AddIcon]
            ["@material-ui/icons/Send" :default SendIcon]
            ["@material-ui/icons/Favorite" :default FavoriteIcon]
            ["@material-ui/icons/Label" :default TagIcon]
            ["@material-ui/icons/Share" :default ShareIcon]
            ["@material-ui/icons/PlaylistAdd" :default AddToCollectionIcon]))

(def ^:private styled
  (styles/style-wrapper
    (fn [theme]
      {:container {:flex "1"}
       :list {:outline "none"}
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
       :control-bar {:display "flex"
                     :align-items "center"
                     :margin-bottom 3
                     :height 60}
       :send-btn {:margin-right 15
                  :margin-bottom 15
                  :margin-left "auto"}})))

(defn- action-btn
  ([label icon on-click]
    (action-btn label icon on-click nil))
  ([label icon on-click icon-props]
    [:> Tooltip {:title label :aria-label label}
      [:> IconButton {:aria-label label
                      :onClick on-click}
        [:> icon icon-props]]]))

(defn- filter-tags [input tags]
  (if (zero? (count input))
    tags
    (filter
      #(string/includes? (string/lower-case %) (string/lower-case input))
      tags)))

(defn- tag-selector []
  (let [tag-anchor-el (r/atom nil)
        input-tag (r/atom "")]
    (fn [{:keys [item]
          cur-private-tags :private-tags
          cur-public-tags :public-tags}]
      (let [entered-tag @input-tag
            el @tag-anchor-el
            tags @app-state/tags
            private-tags (filter-tags entered-tag (:user tags))
            public-tags (filter-tags entered-tag (get-in tags [:public :writable]))
            close-menu (fn []
                         (reset! input-tag "")
                         (reset! tag-anchor-el nil))
            apply-tag (fn [tag-opt]
                        (reqs-actions/tag-req item tag-opt)
                        (close-menu))
            is-entered-tag? #(-> %
                                 (string/lower-case)
                                 (= (string/lower-case entered-tag)))
            new-private (when-not (or (empty? entered-tag)
                                      (some is-entered-tag? private-tags))
                          entered-tag)
            new-public (when-not (or (empty? entered-tag)
                                      (some is-entered-tag? public-tags))
                          entered-tag)]
        [:<>
          [action-btn
            "Tag"
            TagIcon
            #(reset! tag-anchor-el (obj/get % "currentTarget"))
            (when (or (not-empty cur-public-tags) (not-empty cur-private-tags)) {:color "primary"})]
          [:> Menu {:anchorEl el
                    :open (some? el)
                    :onClose #(reset! tag-anchor-el nil)
                    :getContentAnchorEl nil
                    :anchorOrigin #js {:vertical "bottom"
                                       :horizontal "left"}}
            [:> MenuItem {}
              [:> TextField {:fullWidth true
                             :label "Tag"
                             :value entered-tag
                             :onKeyDown #(when-not (string/starts-with? (obj/get % "key") "Arrow")
                                           (.stopPropagation %))
                             :onChange #(reset! input-tag (obj/getValueByKeys % #js ["target" "value"]))}]]
            (when (not-empty private-tags)
              [:> ListSubheader "Private tags"])
            (for [tag private-tags
                  :let [tagged (cur-private-tags tag)]]
              ^{:key tag}
              [:> MenuItem {:onClick #(if tagged (close-menu) (apply-tag {:tag tag}))}
                [:> Typography {:color (if tagged "primary" "textPrimary")}
                  (str tag (when tagged " (Already tagged)"))]])
            (when (not-empty public-tags)
              [:> ListSubheader "Public tags"])
            (for [tag public-tags
                  :let [tagged (cur-public-tags tag)]]
              ^{:key tag}
              [:> MenuItem {:onClick #(if tagged (close-menu) (apply-tag {:pub true :tag tag}))}
                [:> Typography {:color (if tagged "primary" "textPrimary")}
                  (str tag (when tagged " (Already tagged)"))]])
            (when (or new-private new-public)
              [:> ListSubheader "Create new tag"])
            (when new-private
              [:> MenuItem {:onClick #(apply-tag {:tag new-private})}
                (str "New private tag \"" new-private "\"")])
            (when new-public
              [:> MenuItem {:onClick #(apply-tag {:pub true :tag new-public})}
                (str "New public tag \"" new-public "\"")])]]))))

(defn- req-card
  [{:keys [styles favorited public-tags private-tags on-visibility-toggled]
    {:keys [id
            date
            host
            path
            method]
    {:keys [qs]
     {req-headers :headers
       req-body :body} :req
      {res-headers :headers
       res-body :body} :res} :details
    :as item} :item}]
  [:> Card {:className (obj/get styles "card")}
    [:> CardHeader
      {:avatar (r/as-element
                  [:> Avatar {:aria-label method
                              :className (obj/get styles "method")}
                    method])
       :action (r/as-element [:div
                               [action-btn "Favorite" FavoriteIcon #(reqs-actions/tag-req item {:fav true}) (when favorited {:color "secondary"})]
                               [tag-selector {:item item
                                              :private-tags (or private-tags #{})
                                              :public-tags (or public-tags #{})}]
                               [action-btn "Add to request collection" AddToCollectionIcon #()]
                               [action-btn "Share" ShareIcon #()]])
       :title (str host path)
       :subheader date}]
    [:> CardContent {:className (obj/get styles "fix-card-content")}
      [req-parts/qs-view "Query Parameters" qs on-visibility-toggled]
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
    (let [{:keys [items tagged-reqs next-req]} @app-state/reqs
          item-count (count items)
          idx (obj/get props "index")
          {:keys [id fingerprint details] :as item} (get items idx)
          tagged-req (get tagged-reqs fingerprint)
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
                             :favorited (:fav tagged-req)
                             :private-tags (:private-tags tagged-req)
                             :public-tags (:public-tags tagged-req)
                             :on-visibility-toggled (if (some? details)
                                                      start-animating
                                                      load-details)}])]))))])))

(defn- load-more-rows []
  (.then (reqs-actions/load-next-items)
    #(when (not= % :stop) (.clearAll cell-measure-cache))))

(defn- req-list []
  (let [list-ref (r/atom nil)]
    (fn [{:keys [size styles theme]}]
      (let [{:keys [items next-req] :as reqs-state} @app-state/reqs]
        [:> InfiniteLoader {:isRowLoaded #(or (nil? next-req) (< (obj/get % "index") (count items)))
                            :threshold 5
                            :minimumBatchSize 10
                            :loadMoreRows load-more-rows
                            :rowCount (if (nil? next-req) (count items) (inc (count items)))
                            :height (obj/get size "height")}
          (fn [scroll-info]
            (r/as-element
              [:> List {:ref #(do (reset! list-ref %)
                                  ((obj/get scroll-info "registerChild") %))
                        :className (obj/get styles "list")
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
        [:> Paper {:elevation 2
                   :className (obj/get styles "control-bar")}
          [:> TextField {:label "Start at"}]]
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
