(ns webhook-explorer.containers.req
  (:require [clojure.core.async :as async]
            [reagent.core :as r]
            [goog.object :as obj]
            [webhook-explorer.components.req-card :as req-card]
            [webhook-explorer.containers.req-editor :as req-editor]
            [webhook-explorer.actions.reqs :as reqs-actions]))

(defn component [{:keys [slug]}]
  (let [req-chan (reqs-actions/load-req slug)
        req-atom (r/atom nil)]
    (async/go
      (let [req (async/<! req-chan)]
        (reset! req-atom req)))
    (fn []
      [:<>
        [req-editor/component]
        [req-card/component
          {:item @req-atom
           :styles #js {}
           :on-visibility-toggled #()}]])))
