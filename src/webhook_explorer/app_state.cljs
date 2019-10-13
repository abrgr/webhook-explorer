(ns webhook-explorer.app-state
  (:require [reagent.core :as r]))

(defonce nav (r/atom {:page :home
                      :params {}}))

(defonce auth (r/atom {:cognito-auth nil}))

(defn logged-in? []
  (-> @auth
      :cognito-auth
      some?))
