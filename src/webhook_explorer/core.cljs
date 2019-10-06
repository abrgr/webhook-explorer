(ns ^:figwheel-hooks webhook-explorer.core
  (:require [reagent.core :as r]
            [webhook-explorer.shims]
            [webhook-explorer.app-state :as app-state]
            [webhook-explorer.routes :as routes]
            [webhook-explorer.containers.home :as home]
            [webhook-explorer.containers.auth :as auth]))

(defn- current-page []
  (case (:page @app-state/nav)
    :auth [auth/component]
    :home [home/component]
    [auth/component]))

(defn- mount []
  (routes/init!)
  (r/render [current-page]
            (js/document.getElementById "app")))

(defn ^:after-load re-render []
  (mount))

(defonce start-up (do (mount) true))
