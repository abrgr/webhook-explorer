(ns webhook-explorer.containers.home
  (:require [reagent.core :as r]
            ["material-ui/styles/MuiThemeProvider" :as mui-theme-provider]
            ["material-ui/RaisedButton" :as Button]
            [webhook-explorer.app-state :as app-state]))

(defn component []
  [:> (aget mui-theme-provider "default")
      [:div
         [:div "hello world"]
         [:> (aget Button "default") {:primary true :label "Hello"}]
         [:div (.stringify js/JSON (clj->js @app-state/auth))]]])
