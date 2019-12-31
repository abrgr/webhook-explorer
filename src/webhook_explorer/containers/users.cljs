(ns webhook-explorer.containers.users
  (:require [clojure.core.async :as async]
            [reagent.core :as r]
            [goog.object :as obj]
            [webhook-explorer.app-state :as app-state]
            [webhook-explorer.styles :as styles]
            [webhook-explorer.actions.users :as users-actions]
            ["moment" :as moment]
            ["@material-ui/core/TableCell" :default TableCell]
            ["react-virtualized/dist/commonjs/AutoSizer" :default AutoSizer]
            ["react-virtualized/dist/commonjs/Table" :default Table]
            ["react-virtualized/dist/commonjs/Table/Column" :default Column]
            ["react-virtualized/dist/commonjs/InfiniteLoader" :default InfiniteLoader]))

(def ^:private styled
  (styles/style-wrapper
    (fn [theme]
      {:flex-container {:display "flex"
                        :align-items "center"}
       :no-outline {:outline "none"}
       :container {:width "80%"
                   :height "100%"
                   :minWidth "480px"
                   :maxWidth "768px"
                   :margin "25px auto"}})))

(def ^:private row-height 48)

(def ^:private cols
  (sorted-map
    :email {:label "Email"}
    :enabled {:label "Enabled"}
    :role {:label "Role"}))

(defn- header-renderer [props]
  (r/as-element
    [:> TableCell
      {:component "div"
       :variant "head"
       :style #js {:height row-height :display "flex" :flex 1 :alignItems "center"}}
      [:span (obj/get props "label")]]))

(defn- cell-renderer [props]
  (r/as-element
    [:> TableCell
      {:component "div"
       :variant "body"
       :style #js {:height row-height :display "flex" :flex 1 :alignItems "center"}}
      (obj/get props "cellData" "")]))

(defn- get-cell-data [props]
  (let [row-data (obj/get props "rowData")
        data-key (obj/get props "dataKey")]
    (str (get row-data (keyword data-key)))))

(defn- load-more-rows []
  (users-actions/load-next-users))

(defn- -component [{:keys [styles]}]
  (let [{:keys [users next-req]} @app-state/users
        row-count (if (nil? next-req) (count users) (inc (count users)))]
    [:div {:className (obj/get styles "container")}
      [:> AutoSizer
        (fn [size]
          (let [height (obj/get size "height")
                width (obj/get size "width")]
            (r/as-element
              [:> InfiniteLoader {:isRowLoaded #(or (nil? next-req) (< (obj/get % "index") (count users)))
                                  :threshold 5
                                  :minimumBatchSize 10
                                  :loadMoreRows load-more-rows
                                  :rowCount row-count
                                  :height height}
                (fn [scroll-info]
                  (r/as-element
                    [:> Table
                      {:ref #((obj/get scroll-info "registerChild") %)
                       :gridClassName (obj/get styles "no-outline")
                       :height height
                       :width width
                       :rowClassName (obj/get styles "flex-container")
                       :onRowsRendered (obj/get scroll-info "onRowsRendered")
                       :rowCount row-count
                       :rowHeight row-height
                       :headerHeight row-height
                       :rowGetter #(get-in @app-state/users [:users (obj/get % "index")])}
                      (for [[col {:keys [label]}] cols]
                        ^{:key col}
                        [:> Column
                          {:headerRenderer header-renderer
                           :cellRenderer cell-renderer
                           :cellDataGetter get-cell-data
                           :label label
                           :flexGrow 1
                           :width 120
                           :dataKey col}])]))])))]]))

(defn component []
  [styled {} -component])
