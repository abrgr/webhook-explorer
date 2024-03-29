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

(defextroute hist :auth auth-path nav-to-auth nil "/" [query-params]
  (reset! app-state/nav {:page :auth
                         :params (select-keys query-params [:failure])}))

(defn require-login []
  (when-not (app-state/logged-in?)
    (nav-to-auth)
    true))

(defextroute hist :reqs reqs-path nav-to-reqs [require-login] "/reqs" [query-params]
  (reset! app-state/nav {:page :reqs
                         :params (select-keys query-params [:latest-date :all :fav :pub :tag])}))

(defextroute hist :users users-path nav-to-users [require-login] "/users" []
  (reset! app-state/nav {:page :users}))

(defextroute hist :handlers handlers-path nav-to-handlers [require-login] "/handlers" []
  (reset! app-state/nav {:page :handlers}))

(defextroute
  hist
  :edit-handler
  edit-handler-path
  nav-to-edit-handler
  [require-login]
  "/handlers/edit/:match-type/:domain/:method/*path"
  [match-type domain method path]
  (let [params {:proto "https"
                :match-type match-type
                :domain domain
                :method method
                :path path}]
    (reset! app-state/nav {:page :edit-handler
                           :params params})
    (xs/send app-state/handler {:type :reset :params params})))

(defextroute
  hist
  :new-handler
  new-handler-path
  nav-to-new-handler
  [require-login]
  "/handlers/new"
  []
  (reset! app-state/nav {:page :edit-handler})
  (xs/send app-state/handler {:type :reset}))

(defextroute hist :req req-path nav-to-req [require-login] "/req/:slug" [slug]
  (reset! app-state/nav {:page :req :params {:slug slug}}))

(defextroute hist :packages packages-path nav-to-packages [require-login] "/packages" []
  (reset! app-state/nav {:page :packages})
  (xs/send app-state/packages {:type :reset}))

(defextroute hist :new-package new-package-path nav-to-new-package [require-login] "/packages/new" []
  (reset! app-state/nav {:page :edit-package})
  (xs/send app-state/edit-package {:type :reset}))

(defextroute hist :edit-package edit-package-path nav-to-edit-package [require-login] "/packages/edit/:name" [name]
  (reset! app-state/nav {:page :edit-package})
  (xs/send app-state/edit-package {:type :reset :params {:name name}}))

(defextroute hist :package-executions package-executions-path nav-to-package-executions [require-login] "/packages/:name/executions" [name]
  (reset! app-state/nav {:page :package-executions})
  (xs/send app-state/package-executions {:type :reset :params {:name name}}))

(defextroute hist :package-execution package-execution-path nav-to-package-execution [require-login] "/packages/:name/executions/:execution-id" [name execution-id]
  (reset! app-state/nav {:page :package-execution})
  (xs/send app-state/package-execution {:type :reset :params {:name name :execution-id execution-id}}))

(defn init! []
  (defroute "*" []
    (secretary/dispatch! (auth-path)))

  (hook-browser-navigation!))

(init/register-init 1 init!)
