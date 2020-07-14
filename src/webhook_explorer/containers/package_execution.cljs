(ns webhook-explorer.containers.package-execution
  (:require [clojure.core.async :as async]
            [clojure.string :as string]
            [reagent.core :as r]
            [goog.object :as obj]
            [webhook-explorer.app-state :as app-state]
            [webhook-explorer.xstate :as xs]
            [webhook-explorer.nav-to :as nav-to]
            [webhook-explorer.styles :as styles]
            [webhook-explorer.icons :as icons]
            [webhook-explorer.actions.handlers :as handlers-actions]
            [webhook-explorer.components.table-page :as table-page]
            ["@material-ui/core/Dialog" :default Dialog]
            ["@material-ui/core/DialogActions" :default DialogActions]
            ["@material-ui/core/DialogContent" :default DialogContent]
            ["@material-ui/core/DialogContentText" :default DialogContentText]
            ["@material-ui/core/DialogTitle" :default DialogTitle]
            ["@material-ui/core/Button" :default Button]
            ["@material-ui/core/TextField" :default TextField]
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
   :link {:label "Open"}))

; NOTE: this is very messy. We prefix every col with in/ or out/ and then we resolve the path into cell-data here.
(defn- cell-renderer [pkg-name {:keys [col empty-row? row-data cell-data]}]
  (r/as-element
   [:> TableCell
    {:component "div"
     :variant "body"
     :style #js {:height row-height :display "flex" :flex 1 :alignItems "center"}}
    (if empty-row?
      (when (= col :in/date)
        [:> CircularProgress])
      (get-in row-data (conj ({"in" [:req :inputs] "out" [:res :results]} (namespace col)) (-> col name keyword))))]))

(defn- no-rows-renderer [styles]
  (r/as-element
   [:div {:className (obj/get styles "no-items-container")}
    [:> icons/HandlerConfigIcon {:style #js {:fontSize 100}
                                 :color "disabled"}]
    [:> Typography {:variant "h4"
                    :className (obj/get styles "disabled")}
     "No executions yet"]
    [:> CircularProgress]]))

(defn prefix-map-keys [m prefix]
  (->> m
       (map
        (fn [[k v]]
          [(keyword prefix (name k)) v]))
       (into {})))

(defn- -component [{:keys [styles svc state]}]
  [:<>
   (xs/case state
     :error
     "Error retrieving execution set"
     (let [{{:keys [executions cols next-req error params]} :context} state]
       [table-page/component
        {:row-height row-height
         :items executions
         :next-req next-req
         :load-more-items #(do (xs/send svc {:type :load-executions})
                               nil)
         :get-row-by-idx (partial get executions)
         :cols (merge (-> :inputs cols (prefix-map-keys "in")) (-> :outputs cols (prefix-map-keys "out")))
         :no-rows-renderer (partial no-rows-renderer styles)
         :cell-renderer (partial cell-renderer (:name params))}]))])

(defn component []
  [xs/with-svc {:svc app-state/package-execution}
   (fn [state]
     [styled
      {:svc app-state/package-execution
       :state state}
      -component])])
