(ns webhook-explorer.containers.home
  (:require [reagent.core :as r]
            [webhook-explorer.app-state :as app-state]
            [webhook-explorer.styles :as styles]
            ["@material-ui/core/Card" :default Card]
            ["@material-ui/core/CardActions" :default CardActions]
            ["@material-ui/core/CardContent" :default CardContent]
            ["@material-ui/core/Typography" :default Typography]))

(def styled
  (styles/style-wrapper
    {:card {:width "80%"
            :minWidth "480px"
            :maxWidth "768px"
            :margin "25px auto"}
     :date {:fontSize 14}
     :method {:marginBottom 12}}))

(defn- req-card [{:keys [item styles]}]
  [:> Card {:className (.-card styles)}
    [:> CardContent
      [:> Typography {:className (.-date styles)
                      :color "textSecondary"}
        (:date item)]
      [:> Typography {:variant "h5"
                      :component "h2"}
        (:path item)]
      [:> Typography {:className (.-method styles)
                      :component "h2"}
        (:method item)]]])

(defn- -component [{:keys [styles]}]
  [:div
     (for [item (:items @app-state/reqs)]
       ^{:key (:id item)} [req-card {:item item :styles styles}])])

(defn component []
  [styled {} -component])
