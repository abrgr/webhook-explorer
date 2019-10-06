(ns webhook-explorer.routes
  (:require-macros [secretary.core :refer [defroute]])
  (:import goog.history.Html5History)
  (:require [secretary.core :as secretary]
            [goog.events :as events]
            [goog.history.EventType :as EventType]
            [reagent.core :as reagent]
            [webhook-explorer.app-state :as app-state]))

(defn- hook-browser-navigation! []
  (doto (Html5History.)
        (.setPathPrefix (str js/window.location.protocol
                             "//"
                             js/window.location.host))
        (.setUseFragment false)
        (events/listen
           EventType/NAVIGATE
           (fn [event]
             (secretary/dispatch! (str js/window.location.pathname js/window.location.search))))
        (.setEnabled true)))

(defn init! []
  (defroute "/" []
    (swap! app-state/nav assoc :page :home))
  
  (hook-browser-navigation!))
