(ns webhook-explorer.containers.handlers
  (:require [clojure.core.async :as async]
            [clojure.string :as string]
            [reagent.core :as r]
            [goog.object :as obj]
            [webhook-explorer.app-state :as app-state]
            [webhook-explorer.routes :as routes]
            [webhook-explorer.styles :as styles]
            [webhook-explorer.actions.handlers :as handlers-actions]
            [webhook-explorer.components.infinite-table :as infinite-table]
            ["@material-ui/core/Button" :default Button]
            ["@material-ui/core/CircularProgress" :default CircularProgress]
            ["@material-ui/core/TableCell" :default TableCell]))

(def ^:private row-height 64)

(def ^:private cols
  (array-map
    :domain {:label "Domain"}
    :method {:label "Method"}
    :path {:label "Path"}
    :match-type {:label "Edit"}))

(defn- edit-links [{{:keys [domain path has-exact-handler has-prefix-handler]} :handler}]
  [:<>
    (when has-exact-handler
      [:> Button {:on-click #(routes/nav-to-edit-handler {:domain domain :handler-path path :match-type "exact"})
                  :color "primary"}
        "Exact"])
    (when has-prefix-handler
      [:> Button {:on-click #(routes/nav-to-edit-handler {:domain domain :handler-path path :match-type "prefix"})
                  :color "primary"}
        "Prefix"])])

(defn- cell-renderer [{:keys [col empty-row? row-data cell-data]}]
  (r/as-element
    [:> TableCell
      {:component "div"
       :variant "body"
       :style #js {:height row-height :display "flex" :flex 1 :alignItems "center"}}
      (if empty-row?
        (when (= col :domain)
          [:> CircularProgress])
        (case col
          :match-type [edit-links {:handler row-data}]
          :method (-> cell-data str string/capitalize)
          (str cell-data)))]))

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
