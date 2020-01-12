(ns webhook-explorer.containers.home
  (:require [clojure.core.async :as async]
            [reagent.core :as r]
            [goog.object :as obj]
            [webhook-explorer.app-state :as app-state]
            [webhook-explorer.containers.req-editor :as req-editor]
            [webhook-explorer.components.req-parts :as req-parts]
            [webhook-explorer.components.tag-selector :as tag-selector]
            [webhook-explorer.styles :as styles]
            [webhook-explorer.actions.reqs :as reqs-actions]
            ["moment" :as moment]
            ["react-virtualized/dist/commonjs/AutoSizer" :default AutoSizer]
            ["react-virtualized/dist/commonjs/CellMeasurer" :refer [CellMeasurer CellMeasurerCache]]
            ["react-virtualized/dist/commonjs/List" :default List]
            ["react-virtualized/dist/commonjs/InfiniteLoader" :default InfiniteLoader]
            ["@material-ui/core/colors" :as colors]
            ["@material-ui/pickers" :as pickers]
            ["@material-ui/core/Avatar" :default Avatar]
            ["@material-ui/core/Button" :default Button]
            ["@material-ui/core/Card" :default Card]
            ["@material-ui/core/CardActions" :default CardActions]
            ["@material-ui/core/CardContent" :default CardContent]
            ["@material-ui/core/CardHeader" :default CardHeader]
            ["@material-ui/core/Collapse" :default Collapse]
            ["@material-ui/core/IconButton" :default IconButton]
            ["@material-ui/core/Typography" :default Typography]
            ["@material-ui/core/CircularProgress" :default CircularProgress]
            ["@material-ui/core/Select" :default Select]
            ["@material-ui/core/FormControl" :default FormControl]
            ["@material-ui/core/InputLabel" :default InputLabel]
            ["@material-ui/core/Tooltip" :default Tooltip]
            ["@material-ui/core/Fab" :default FloatingActionButton]
            ["@material-ui/core/Paper" :default Paper]
            ["@material-ui/core/styles" :refer [withTheme] :rename {withTheme with-theme}]
            ["@material-ui/core/MenuItem" :default MenuItem]
            ["@material-ui/icons/Dashboard" :default RequestsIcon]
            ["@material-ui/icons/Add" :default AddIcon]
            ["@material-ui/icons/Send" :default SendIcon]
            ["@material-ui/icons/Favorite" :default FavoriteIcon]
            ["@material-ui/icons/Label" :default TagIcon]
            ["@material-ui/icons/Share" :default ShareIcon]
            ["@material-ui/icons/PlaylistAdd" :default AddToCollectionIcon]))

(defn- background-style [theme color]
  {:color (.getContrastText (obj/get theme "palette") color)
   :backgroundColor color})

(def ^:private styled
  (styles/style-wrapper
    (fn [theme]
      (let [status-style {:width 60 :height 60 :margin 10}]
        {:container {:flex "1"}
         :list {:outline "none"}
         :card-container {:display "flex"
                          :justifyContent "center"}
         :no-items-container {:display "flex"
                              :flexDirection "column"
                              :height "100%"
                              :alignItems "center"
                              :justifyContent "center"}
         :disabled {:color "rgba(0, 0, 0, 0.26)"}
         :card {:width "80%"
                :minWidth "480px"
                :maxWidth "768px"
                :margin "25px auto"}
         :card-action-btn {:margin (.spacing theme 1)}
         :date {:fontSize 14}
         :status-info (merge
                        (background-style theme (aget colors/yellow 500))
                        status-style)
         :status-success (merge
                           (background-style theme (aget colors/green 500))
                           status-style)
         :status-redirect (merge
                            (background-style theme (aget colors/grey 500))
                            status-style)
         :status-client-error (merge
                                (background-style theme (aget colors/pink 500))
                                status-style)
         :status-server-error (merge
                                (background-style theme (aget colors/red 500))
                                status-style)
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
                       :min-height 60
                       :padding-left 60
                       :padding-right 60}
         :control-bar-control {:width 238
                               :margin-right 60}
         :send-btn {:margin-right 15
                    :margin-bottom 15
                    :margin-left "auto"}}))))

(defn- action-btn
  ([label icon on-click]
    (action-btn label icon on-click nil))
  ([label icon on-click icon-props]
    [:> Tooltip {:title label :aria-label label}
      [:> IconButton {:aria-label label
                      :onClick on-click}
        [:> icon icon-props]]]))

(defn- tag-action-btn [{:keys [on-open-menu any-selected]}]
  [action-btn
    "Tag"
    TagIcon
    on-open-menu
    (when any-selected {:color "primary"})])

(defn- status-class [styles status]
  (cond
    (< status 200) (obj/get styles "status-info")
    (< status 300) (obj/get styles "status-success")
    (< status 400) (obj/get styles "status-redirect")
    (< status 500) (obj/get styles "status-client-error")
    :else          (obj/get styles "status-server-error")))

(defn- req-card
  [{:keys [styles favorited public-tags private-tags on-visibility-toggled]
    {:keys [id
            date
            host
            path
            method
            status]
    {:keys [qs]
     {req-headers :headers
      req-cookies :cookies
      req-body :body
      {:keys [fields files]} :form} :req
     {res-headers :headers
      res-body :body} :res
     :as details} :details
    :as item} :item}]
  [:> Card {:className (obj/get styles "card")}
    [:> CardHeader
      {:avatar (r/as-element
                  [:> Avatar {:aria-label status
                              :className (status-class styles status)}
                    status])
       :action (r/as-element [:div
                               [action-btn "Favorite" FavoriteIcon #(reqs-actions/tag-req item {:fav true}) (when favorited {:color "secondary"})]
                               [tag-selector/component
                                 {:on-select-tag (partial reqs-actions/tag-req item)
                                  :rw :writable
                                  :target-component tag-action-btn
                                  :private-tags private-tags
                                  :public-tags public-tags
                                  :allow-creation true
                                  :selected-label "(Already tagged)"}]
                               [action-btn "Add to request collection" AddToCollectionIcon #()]
                               [action-btn "Share" ShareIcon #()]])
       :title (str method " " host path)
       :subheader date}]
    [:> CardContent {:className (obj/get styles "fix-card-content")}
      [req-parts/qs-view "Query Parameters" qs on-visibility-toggled]
      [req-parts/headers-view "Request Headers" req-headers on-visibility-toggled]
      (let [cs (or
                 (some->> req-cookies
                          (map (fn [[k {:keys [value]}]] [k value]))
                          (into {}))
                 (and details {}))] ; cookies might be nil, set to {} if cookies nil but we have details
        [req-parts/cookies-view
          "Request Cookies"
          cs
          on-visibility-toggled])
      [req-parts/body-view
        "Request Body"
        (req-parts/make-bodies
          {:raw {:label "Raw" :body req-body}
           :fields {:label "Form" :body fields}})
        req-headers
        on-visibility-toggled]
      [req-parts/headers-view "Response Headers" res-headers on-visibility-toggled]
      [req-parts/body-view "Response Body" (req-parts/make-bodies {:raw {:label "Raw" :body res-body}}) res-headers on-visibility-toggled]]
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
                      (advance-animation (* 2 (obj/getValueByKeys theme #js ["transitions" "duration" "standard"]))))
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

(defn- no-rows-renderer [styles]
  (r/as-element
    [:div {:className (obj/get styles "no-items-container")}
      [:> RequestsIcon {:style #js {:fontSize 100}
                        :color "disabled"}]
      [:> Typography {:variant "h4"
                      :className (obj/get styles "disabled")}
        "No matching requests"]]))

(defn- req-list []
  (let [list-ref (r/atom nil)]
    (fn [{:keys [size styles theme set-refetch-items]}]
      (let [{:keys [items next-req] :as reqs-state} @app-state/reqs]
        [:> InfiniteLoader {:ref (fn [inf-ldr]
                                   (set-refetch-items
                                     (if (nil? inf-ldr)
                                       #()
                                       #(.resetLoadMoreRowsCache inf-ldr true))))
                            :isRowLoaded #(let [{:keys [items next-req] :as reqs-state} @app-state/reqs]
                                            (or (nil? next-req) (< (obj/get % "index") (count items))))
                            :threshold 5
                            :minimumBatchSize 10
                            :loadMoreRows load-more-rows
                            :rowCount (if (nil? next-req) (count items) (inc (count items)))
                            :height (obj/get size "height")}
          (fn [scroll-info]
            (r/as-element
              [:> List {:ref #(do (reset! list-ref %)
                                  ((obj/get scroll-info "registerChild") %))
                        :noRowsRenderer (partial no-rows-renderer styles)
                        :className (obj/get styles "list")
                        :onRowsRendered (obj/get scroll-info "onRowsRendered")
                        :rowRenderer (partial row-renderer styles theme #(when-not (nil? @list-ref) (.recomputeGridSize @list-ref %)))
                        :height (obj/get size "height")
                        :width (obj/get size "width")
                        :rowCount (if (nil? next-req) (count items) (inc (count items)))
                        :rowHeight (obj/get cell-measure-cache "rowHeight")
                        :deferredMeasurementCache cell-measure-cache}]))]))))

(defn- tag-selector-select
  [styles props]
  (let [id (str (random-uuid))]
    (fn [{:keys [on-open-menu any-selected selected-priv-tags selected-pub-tags extra-tags]}]
      (let [sel-priv-tag (first selected-priv-tags)
            sel-pub-tag (first selected-pub-tags)
            sel (or sel-priv-tag sel-pub-tag)
            sel-label (or (get extra-tags sel-priv-tag sel-priv-tag) sel-pub-tag)]
        [:> FormControl {:classes {:root (obj/get styles "control-bar-control")}}
          [:> InputLabel {:id id} "Tag"]
          [:> Select
            {:labelId id
             :value sel
             :onChange #()
             :open false
             :onOpen on-open-menu}
            [:> MenuItem {:value sel} sel-label]]]))))

(defn- control-bar []
  (fn [{:keys [styles refetch-items]}]
    (let [{{:keys [latest-date all fav tag pub]} :params} @app-state/nav
          all-selected (or all
                           (every? nil? [latest-date all fav tag pub]))
          fmt "YYYY-MM-DD"
          extra-tags (array-map
                       "*all*" "All"
                       "*fav*" "My Favorites")]
      [:> Paper {:elevation 2
                 :className (obj/get styles "control-bar")}
        [tag-selector/component {:target-component (partial tag-selector-select styles)
                                 :on-select-tag #(do (reqs-actions/select-tag (case (:tag %)
                                                                                 "*all*" {:all true}
                                                                                 "*fav*" {:fav true}
                                                                                 %))
                                                      (refetch-items))
                                 :rw :readable
                                 :private-tags #{(cond
                                                    all-selected "*all*"
                                                    fav "*fav*"
                                                    (not pub) tag)}
                                 :public-tags (if pub #{tag} nil)
                                 :extra-tags extra-tags
                                 :selected-label "(Selected)"}]
        [:> FormControl {:classes {:root (obj/get styles "control-bar-control")}}
          [:> pickers/KeyboardDatePicker {:label "Latest date (UTC)"
                                          :format fmt
                                          :value (or latest-date (-> (moment.) (.format fmt)))
                                          :onChange #(do (reqs-actions/set-latest-date %)
                                                         (refetch-items))
                                          :variant "inline"
                                          :disableFuture true
                                          :autoOk true}]]])))

(defn- home []
  (let [refetch-items (r/atom nil)
        set-refetch-items (partial reset! refetch-items)]
    (fn [{:keys [styles theme]}]
      (r/as-element
        [:<>
          [req-editor/component]
          [control-bar {:styles styles
                        :refetch-items #(when-let [r @refetch-items]
                                          (r))}]
          [:div {:className (obj/get styles "container")}
            [:> AutoSizer
              (fn [size]
                (r/as-element
                  [req-list {:size size
                             :styles styles
                             :theme theme
                             :set-refetch-items set-refetch-items}]))]]]))))
(defn- -component [props]
  (let [styles (obj/get props "styles")
        theme (obj/get props "theme")]
    (r/as-element
      [home {:styles styles
             :theme theme}])))

(def ^:private -component-with-styles-and-theme (with-theme -component))

(defn -component-with-styles [{:keys [styles]}]
  [:> -component-with-styles-and-theme {:styles styles}])

(defn component []
  [styled {} -component-with-styles])
