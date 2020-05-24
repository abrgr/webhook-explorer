(ns webhook-explorer.containers.packages
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
   :name {:label "Request Package"}))

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
      (when (= col :name)
        [:> CircularProgress])
      cell-data)]))

(defn- no-rows-renderer [styles]
  (r/as-element
   [:div {:className (obj/get styles "no-items-container")}
    [:> icons/HandlerConfigIcon {:style #js {:fontSize 100}
                                 :color "disabled"}]
    [:> Typography {:variant "h4"
                    :className (obj/get styles "disabled")}
     "No request packages created yet"]]))

(defn- -component [{:keys [styles svc state]}]
  (xs/case state
    :ready 
    (let [{{:keys [packages next-req error]} :context} state]
      [table-page/component
       {:create-btn-content "Create request package"
        :on-create routes/nav-to-new-package
        :row-height row-height
        :items packages
        :next-req next-req
        :load-more-items (xs/send svc {:type :load-packages})
        :get-row-by-idx (partial nth packages)
        :cols cols
        :no-rows-renderer (partial no-rows-renderer styles)
        :cell-renderer cell-renderer}])))

(defn component []
  [xs/with-svc {:svc app-state/packages}
    (fn [state]
      [styled
       {:svc app-state/packages
        :state state}
       -component])])
