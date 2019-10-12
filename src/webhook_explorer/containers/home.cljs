(ns webhook-explorer.containers.home
  (:require [reagent.core :as r]
            ["@material-ui/core/Button" :default Button]
            [webhook-explorer.app-state :as app-state]))

(defn component []
  [:div
     [:div "hello world"]
     [:> Button {:color "primary" :variant "contained"} "Hello world"]
     [:div (.stringify js/JSON (clj->js @app-state/auth))]])
