(ns webhook-explorer.routes
  (:require-macros [secretary.core :refer [defroute]]
                   [webhook-explorer.routes :refer [defextroute]])
  (:import goog.history.Html5History)
  (:require [secretary.core :as secretary]
            [goog.object :as obj]
            [goog.events :as events]
            [goog.history.EventType :as EventType]
            [reagent.core :as reagent]
            [webhook-explorer.init :as init]
            [webhook-explorer.xstate :as xs]
            [webhook-explorer.app-state :as app-state]))

(def ^:private hist
  (Html5History.
    nil
    #js {:retrieveToken (fn [prefix loc]
                          (subs (obj/get loc "pathname") (count prefix)))
         :createUrl (fn [token prefix url]
                      (str prefix token))}))

(defn- hook-browser-navigation! []
  (doto hist
        (.setPathPrefix (str js/window.location.protocol
                             "//"
                             js/window.location.host))
        (.setUseFragment false)
        (events/listen
           EventType/NAVIGATE
           (fn [event]
             (secretary/dispatch! (str js/window.location.pathname js/window.location.search))))
        (.setEnabled true)))

(defextroute hist auth-path nav-to-auth nil "/" [query-params]
  (reset! app-state/nav {:page :auth
                         :params (select-keys query-params [:failure])}))

(defn require-login []
  (when-not (app-state/logged-in?)
    (nav-to-auth)
    true))

(defextroute hist reqs-path nav-to-reqs [require-login] "/reqs" [query-params]
  (reset! app-state/nav {:page :reqs
                         :params (select-keys query-params [:latest-date :all :fav :pub :tag])}))

(defextroute hist users-path nav-to-users [require-login] "/users" []
  (reset! app-state/nav {:page :users}))

(defextroute hist handlers-path nav-to-handlers [require-login] "/handlers" []
  (reset! app-state/nav {:page :handlers}))

(defextroute
  hist
  edit-handler-path
  nav-to-edit-handler
  [require-login]
  "/edit-handler/:match-type/:domain/*handler-path"
  [match-type domain handler-path]
  (let [params {:match-type match-type
                :domain domain
                :handler-path handler-path}]
    (reset! app-state/nav {:page :edit-handler
                           :params params})
    (xs/send app-state/handler {:type :reset :params params})))

(defextroute hist req-path nav-to-req [require-login] "/req/:slug" [slug]
  (reset! app-state/nav {:page :req :params {:slug slug}}))

(defn init! []
  (defroute "*" []
    (secretary/dispatch! (auth-path)))

  (hook-browser-navigation!))

(init/register-init 1 init!)
