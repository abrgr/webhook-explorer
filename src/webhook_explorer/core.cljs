(ns ^:figwheel-hooks webhook-explorer.core
  (:require [reagent.core :as r]
            ["@material-ui/core/CssBaseline" :default CssBaseline]
            [webhook-explorer.shims]
            [webhook-explorer.init :as init]
            [webhook-explorer.app-state :as app-state]
            [webhook-explorer.routes :as routes]
            [webhook-explorer.containers.app-bar :as app-bar]
            [webhook-explorer.containers.home :as home]
            [webhook-explorer.containers.auth :as auth]))

(defn- current-page []
  (case (:page @app-state/nav)
    :auth [auth/component]
    :home [home/component]
    [auth/component]))

(defn- page []
  [:<>
    [:> CssBaseline]
    [app-bar/component]
    [current-page]])

(defn- mount []
  (routes/init!)
  (r/render [page]
            (js/document.getElementById "app")))

(defn ^:after-load re-render []
  (mount))

(defonce start-up (do (init/fire-init) (mount) true))
