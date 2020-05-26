(ns webhook-explorer.components.card-list
  (:require [goog.object :as obj]
            [reagent.core :as r]
            [webhook-explorer.styles :as styles]
            [webhook-explorer.xstate :as xs]
            [webhook-explorer.components.add-box :as add-box]
            ["@material-ui/core/CircularProgress" :default CircularProgress]))

(def ^:private styled
  (styles/style-wrapper
   (fn [theme]
     {:flex-container {:display "flex"
                       :align-items "center"
                       :justify-content "center"}
      :container {:width "80%"
                  :height "100%"
                  :minWidth "480px"
                  :maxWidth "768px"
                  :margin "25px auto"}
      :item-container {:marginTop 48
                       :padding 20}})))

(defn main-list [{:keys [styles
                         state
                         add-item-title
                         on-add-item
                         items
                         item-renderer
                         svc
                         preamble-component
                         postamble-component]}]
  [:<>
   (when preamble-component
     [preamble-component
      {:state state
       :items items
       :svc svc}])
   (map-indexed
    (fn [idx item]
      ^{:key idx}
      [item-renderer
       {:idx idx
        :items items
        :class-name (obj/get styles "item-container")
        :svc svc
        :state state
        :item item}])
    items)
   [add-box/component
    {:class-name (obj/get styles "item-container")
     :on-click on-add-item
     :title add-item-title}]
   (when postamble-component
     [postamble-component
      {:state state
       :items items
       :svc svc}])])

(defn component* [{:keys [styles
                          svc
                          item-renderer
                          preamble-component
                          postamble-component
                          failed-component
                          add-item-title
                          on-add-item
                          state
                          state->items
                          ready-state
                          failed-state]}]
  [:div
   {:className (obj/get styles "container")}
   (xs/case state
     failed-state [failed-component {:state state :svc svc}]
     ready-state [main-list {:styles styles
                             :state state
                             :svc svc
                             :preamble-component preamble-component
                             :postamble-component postamble-component
                             :add-item-title add-item-title
                             :on-add-item on-add-item
                             :item-renderer item-renderer
                             :items (state->items state)}]
     [:div
      {:class (obj/get styles "flex-container")}
      [:> CircularProgress]])])

(defn component [props]
  [styled props component*])

(defn template-var-map->simple-map [m]
  (some->> m
           (map (fn [[k {:keys [template-var]}]] [k template-var]))
           (into {})))
