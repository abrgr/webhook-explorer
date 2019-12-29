(ns webhook-explorer.containers.auth
  (:require [reagent.core :as r]
            [webhook-explorer.app-state :as app-state]))

(defn component []
  (let [{{:keys [failure]} :params} @app-state/nav]
    [:<>
      (when failure
        [:div "Failed to sign in"])
      [:div "Sign in!"]]))
