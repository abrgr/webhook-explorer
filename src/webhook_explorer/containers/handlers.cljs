(ns webhook-explorer.containers.handlers
  (:require [clojure.core.async :as async]
            [clojure.string :as string]
            [reagent.core :as r]
            [goog.object :as obj]
            [webhook-explorer.app-state :as app-state]
            [webhook-explorer.routes :as routes]
            [webhook-explorer.styles :as styles]
            [webhook-explorer.icons :as icons]
            [webhook-explorer.actions.handlers :as handlers-actions]
            [webhook-explorer.components.table-page :as table-page]
            ["@material-ui/core/Button" :default Button]
            ["@material-ui/core/Typography" :default Typography]
            ["@material-ui/core/CircularProgress" :default CircularProgress]
            ["@material-ui/core/TableCell" :default TableCell]))

(def ^:private row-height 64)

(def ^:private styled
  (styles/style-wrapper
   (fn [theme]
     {:no-items-container {:display "flex"
                           :flexDirection "column"
                           :height "100%"
                           :alignItems "center"
                           :justifyContent "center"}
      :disabled {:color "rgba(0, 0, 0, 0.26)"}})))

(def ^:private cols
  (array-map
   :domain {:label "Domain"}
   :method {:label "Method"}
   :path {:label "Path"}
   :match-type {:label "Edit"}))

(defn- edit-links [{{:keys [domain path method has-exact-handler has-prefix-handler]} :handler}]
  [:<>
   (when has-exact-handler
     [:> Button
      {:on-click #(routes/nav-to-edit-handler {:domain domain
                                               :path path
                                               :method method
                                               :match-type "exact"})
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

(defn- no-rows-renderer [styles]
  (r/as-element
   [:div {:className (obj/get styles "no-items-container")}
    [:> icons/HandlerConfigIcon {:style #js {:fontSize 100}
                                 :color "disabled"}]
    [:> Typography {:variant "h4"
                    :className (obj/get styles "disabled")}
     "No handlers configured yet"]]))

(defn- -component [{:keys [styles]}]
  (let [{:keys [handlers next-req error]} @app-state/handlers]
    [table-page/component
     {:create-btn-content "Create handler"
      :on-create routes/nav-to-new-handler
      :row-height row-height
      :items handlers
      :next-req next-req
      :load-more-items handlers-actions/load-next-handlers
      :get-row-by-idx #(get-in @app-state/handlers [:handlers %])
      :cols cols
      :no-rows-renderer (partial no-rows-renderer styles)
      :cell-renderer cell-renderer}]))

(defn component []
  [styled {} -component])
