(ns webhook-explorer.containers.home
  (:require [reagent.core :as r]
            [webhook-explorer.actions.auth :as auth-actions]
            [webhook-explorer.app-state :as app-state]))

(defn component []
  [:div
     [:div "hello world"]
     [:div (.stringify js/JSON (clj->js @app-state/auth))]])
