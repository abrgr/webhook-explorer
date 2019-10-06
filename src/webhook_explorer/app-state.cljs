(ns webhook-explorer.app-state
  (:require [reagent.core :as r]))

(defonce nav (r/atom {:page :home
                      :params {}}))
