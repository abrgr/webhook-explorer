(ns webhook-explorer.app-state
  (:require [reagent.core :as r]))

(defonce nav (r/atom {:page :home
                      :params {}}))

(defonce auth (r/atom {:user-data nil :cognito-session nil}))

(defn logged-in? []
  (some? (:cognito-session @auth)))
