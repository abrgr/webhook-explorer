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
            [webhook-explorer.containers.handlers :as handlers]
            [webhook-explorer.containers.edit-handler :as edit-handler]
            [webhook-explorer.containers.edit-package :as edit-package]
            [webhook-explorer.containers.req :as req]
            [webhook-explorer.containers.auth :as auth]))

(defn- current-page []
  (let [{:keys [page params]} @app-state/nav]
    (case page
      :auth [auth/component]
      :reqs [home/component]
      :users [users/component]
      :handlers [handlers/component]
      :edit-handler [edit-handler/component]
      :edit-package [edit-package/component]
      :req [req/component params]
      [auth/component])))

(defn- page []
  [:div {:style #js {:display "flex" :flexDirection "column" :height "100%"}}
   [:> pickers/MuiPickersUtilsProvider {:utils MomentUtils}
    [:> CssBaseline]
    [app-bar/component]
    [current-page]]])

(defn- mount []
  (r/render [page]
            (js/document.getElementById "app")))

(defn ^:dev/after-load re-render []
  (mount))

(defonce start-up (do (init/fire-init) (mount) true))
