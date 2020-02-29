(ns webhook-explorer.components.infinite-table
  (:require [reagent.core :as r]
            [goog.object :as obj]
            [webhook-explorer.styles :as styles]
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
       :no-outline {:outline "none"}})))

(defn- get-cell-data [props]
  (let [row-data (obj/get props "rowData")
        data-key (obj/get props "dataKey")]
    (get row-data (keyword data-key))))

(defn- default-header-renderer [row-height props]
  (r/as-element
    [:> TableCell
      {:component "div"
       :variant "head"
       :style #js {:height row-height :display "flex" :flex 1 :alignItems "center"}}
      [:span (obj/get props "label")]]))

(defn- cell-renderer-wrapper [cols inner-renderer props]
  (let [row-data (obj/get props "rowData")
        col-index (obj/get props "columnIndex")
        cell-data (obj/get props "cellData")
        colsv (->> cols
                   keys
                   (into []))
        col (get colsv col-index)]
    (inner-renderer {:col col :row-data row-data :cell-data cell-data :empty-row? (nil? row-data)})))

(defn- -component [{:keys [styles row-height items next-req load-more-items get-row-by-idx cols header-renderer cell-renderer] :as p}]
  (let [row-count (if (nil? next-req) (count items) (inc (count items)))]
    [:> AutoSizer
      (fn [size]
        (let [height (obj/get size "height")
              width (obj/get size "width")]
          (r/as-element
            [:> InfiniteLoader {:isRowLoaded #(or (nil? next-req) (< (obj/get % "index") (count items)))
                                :threshold 5
                                :minimumBatchSize 10
                                :loadMoreRows load-more-items
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
                     :rowGetter #(get-row-by-idx (obj/get % "index"))}
                    (for [[col {:keys [label]}] cols]
                      ^{:key col}
                      [:> Column
                        {:headerRenderer (or header-renderer (partial default-header-renderer row-height))
                         :cellRenderer (partial cell-renderer-wrapper cols cell-renderer)
                         :cellDataGetter get-cell-data
                         :label label
                         :flexGrow 1
                         :width 120
                         :dataKey col}])]))])))]))

(defn component [props]
  [styled props -component])
