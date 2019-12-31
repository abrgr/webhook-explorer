(ns ^:figwheel-hooks webhook-explorer.core
  (:require [reagent.core :as r]
            ["@material-ui/core/CssBaseline" :default CssBaseline]
            ["@material-ui/pickers" :as pickers]
            ["@date-io/moment" :as MomentUtils]
            [webhook-explorer.actions.tags] ; TODO: would love not to include this just for side effects
            [webhook-explorer.shims]
            [webhook-explorer.init :as init]
            [webhook-explorer.app-state :as app-state]
            [webhook-explorer.routes :as routes]
            [webhook-explorer.containers.app-bar :as app-bar]
            [webhook-explorer.containers.home :as home]
            [webhook-explorer.containers.users :as users]
            [webhook-explorer.containers.auth :as auth]))

(defn- current-page []
  (case (:page @app-state/nav)
    :auth [auth/component]
    :reqs [home/component]
    :users [users/component]
    [auth/component]))

(defn- page []
  [:div {:style #js {:display "flex" :flexDirection "column" :height "100%"}}
    [:> pickers/MuiPickersUtilsProvider {:utils MomentUtils}
      [:> CssBaseline]
      [app-bar/component]
      [current-page]]])

(defn- mount []
  (r/render [page]
            (js/document.getElementById "app")))

(defn ^:after-load re-render []
  (mount))

(defonce start-up (do (init/fire-init) (mount) true))
