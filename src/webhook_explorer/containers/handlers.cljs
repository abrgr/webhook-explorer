(ns webhook-explorer.containers.handlers
  (:require [clojure.core.async :as async]
            [reagent.core :as r]
            [goog.object :as obj]
            [webhook-explorer.app-state :as app-state]
            [webhook-explorer.styles :as styles]
            [webhook-explorer.actions.handlers :as handlers-actions]
            [webhook-explorer.components.infinite-table :as infinite-table]
            ["@material-ui/core/CircularProgress" :default CircularProgress]
            ["@material-ui/core/TableCell" :default TableCell]))

(def ^:private row-height 64)

(def ^:private cols
  (array-map
    :domain {:label "Domain"}
    :path {:label "Path"}
    :match-type {:label "Match Type"}))

(defn- cell-renderer [{:keys [col empty-row? cell-data]}]
  (r/as-element
    [:> TableCell
      {:component "div"
       :variant "body"
       :style #js {:height row-height :display "flex" :flex 1 :alignItems "center"}}
      (if empty-row?
        (when (= col :domain)
          [:> CircularProgress])
        (str cell-data))]))

(defn component [{:keys [styles]}]
  (let [{:keys [handlers next-req error]} @app-state/handlers]
    [infinite-table/component
      {:row-height row-height
       :items handlers
       :next-req next-req
       :load-more-items handlers-actions/load-next-handlers
       :get-row-by-idx #(get-in @app-state/handlers [:handlers %])
       :cols cols
       :cell-renderer cell-renderer}]))
