(ns webhook-explorer.containers.package-executions
  (:require [clojure.core.async :as async]
            [clojure.string :as string]
            [reagent.core :as r]
            [goog.object :as obj]
            [webhook-explorer.app-state :as app-state]
            [webhook-explorer.xstate :as xs]
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
   :date {:label "Date"}
   :uid {:label "User ID"}
   :id {:label "ID"}))

(defn- cell-renderer [{:keys [col empty-row? row-data cell-data]}]
  (r/as-element
   [:> TableCell
    {:component "div"
     :variant "body"
     :style #js {:height row-height :display "flex" :flex 1 :alignItems "center"}}
    (if empty-row?
      (when (= col :name)
        [:> CircularProgress])
      (case col
        :id
        [:> Button
         {:on-click #(routes/nav-to-edit-package {:name (:name row-data)})}
         cell-data]
        cell-data))]))

(defn- no-rows-renderer [styles]
  (r/as-element
   [:div {:className (obj/get styles "no-items-container")}
    [:> icons/HandlerConfigIcon {:style #js {:fontSize 100}
                                 :color "disabled"}]
    [:> Typography {:variant "h4"
                    :className (obj/get styles "disabled")}
     "No executions yet"]]))

(defn- -component [{:keys [styles svc state]}]
  (xs/case state
    :ready
    (let [{{:keys [executions next-req error]} :context} state]
      [table-page/component
       {:create-btn-content "Create execution"
        :on-create routes/nav-to-new-package
        :row-height row-height
        :items executions
        :next-req next-req
        :load-more-items #(do (xs/send svc {:type :load-executions})
                              nil)
        :get-row-by-idx (partial get executions)
        :cols cols
        :no-rows-renderer (partial no-rows-renderer styles)
        :cell-renderer cell-renderer}])))

(defn component []
  [xs/with-svc {:svc app-state/package-executions}
   (fn [state]
     [styled
      {:svc app-state/package-executions
       :state state}
      -component])])
