(ns ^:figwheel-hooks webhook-explorer.core
  (:require [reagent.core :as r]
            [webhook-explorer.app-state :as app-state]
            [webhook-explorer.routes :as routes]
            [webhook-explorer.containers.home :as home]))

(defn- current-page []
  (case (:page @app-state/nav)
    :home [home/component]
    [home/component]))

(defn- mount []
  (routes/init!)
  (r/render [current-page]
            (js/document.getElementById "app")))

(defn ^:after-load re-render []
  (mount))

(defonce start-up (do (mount) true))
