(ns webhook-explorer.containers.req
  (:require [clojure.core.async :as async]
            [reagent.core :as r]
            [goog.object :as obj]
            [webhook-explorer.styles :as styles]
            [webhook-explorer.components.req-card :as req-card]
            [webhook-explorer.containers.req-editor :as req-editor]
            [webhook-explorer.actions.reqs :as reqs-actions]
            ["@material-ui/core/CircularProgress" :default CircularProgress]))

(def ^:private styled
  (styles/style-wrapper
   (fn [theme]
     (let [status-style {:width 60 :height 60 :margin 10}]
       {:card-container {:display "flex"
                         :justifyContent "center"}}))))

(defn- -component [{:keys [styles slug]}]
  (let [req-chan (reqs-actions/load-req slug)
        req-atom (r/atom nil)]
    (async/go
      (let [req (async/<! req-chan)]
        (reset! req-atom req)))
    (fn []
      (let [{:keys [tags] :as item} @req-atom]
        [:div {:className (obj/get styles "card-container")}
         (if (nil? item)
           [:> CircularProgress]
           [:<>
            [req-editor/component]
            [req-card/component
             {:item item
              :favorited (:fav tags)
              :private-tags (:private-tags tags)
              :public-tags (:public-tags tags)
              :on-visibility-toggled #()}]])]))))

(defn component [params]
  [styled params -component])
