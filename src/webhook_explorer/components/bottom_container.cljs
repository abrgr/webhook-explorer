(ns webhook-explorer.components.bottom-container
  (:require [webhook-explorer.styles :as styles]
            [goog.object :as obj]
            ["@material-ui/core/Paper" :default Paper]
            ["@material-ui/core/Fab" :default Fab]
            ["@material-ui/core/CircularProgress" :default CircularProgress]))

(def height 150)

(def ^:private styled
  (styles/style-wrapper
   (fn [theme]
     {:extended-icon {:marginRight (.spacing theme 1)}
      :btn-container {:margin "auto"}
      :content-container {:flex "1"}
      :bottom-container-spacer {:height (+ height 50)}
      :bottom-container {:position "fixed"
                         :display "flex"
                         :flex-direction "row-reverse"
                         :left 0
                         :right 0
                         :bottom 0
                         :height height
                         :border-top "2px solid #eee"
                         :z-index 100
                         :padding 20}})))

(defn- component* [{:keys [styles
                           on-btn-click
                           btn-loading
                           btn-icon-component
                           btn-title
                           children]}]
  [:> Paper {:className (obj/get styles "bottom-container")}
   [:div {:className (obj/get styles "btn-container")}
    [:> Fab {:variant "extended"
             :color "secondary"
             :onClick on-btn-click}
     (if btn-loading
       [:> CircularProgress]
       [btn-icon-component {:className (obj/get styles "extended-icon")}])
     btn-title]]
   (into
    [:div {:className (obj/get styles "content-container")}]
    children)])

(defn component [props & children]
  [styled (assoc props :children children) component*])

(defn- spacer-component* [{:keys [styles]}]
  [:div {:className (obj/get styles "bottom-container-spacer")}])

(defn spacer-component []
  [styled {} spacer-component*])
