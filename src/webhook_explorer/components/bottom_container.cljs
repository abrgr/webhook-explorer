(ns webhook-explorer.components.bottom-container
  (:require [webhook-explorer.styles :as styles]
            [goog.object :as obj]
            ["@material-ui/core/Paper" :default Paper]
            ["@material-ui/core/Fab" :default Fab]))

(def bottom-container-height 150)

(def ^:private styled
  (styles/style-wrapper
   (fn [theme]
     {:extended-icon {:marginRight (.spacing theme 1)}
      :btn-container {:margin "auto"}
      :bottom-container {:position "fixed"
                         :display "flex"
                         :flex-direction "row-reverse"
                         :left 0
                         :right 0
                         :bottom 0
                         :height bottom-container-height
                         :border-top "2px solid #eee"
                         :z-index 100
                         :padding 20}})))

(defn component* [{:keys [styles
                          on-btn-click
                          btn-icon-component
                          btn-title
                          children]}]
  (into
   [:> Paper {:className (obj/get styles "bottom-container")}]
   (into
    [[:div {:className (obj/get styles "btn-container")}
      [:> Fab {:variant "extended"
               :color "secondary"
               :onClick on-btn-click}
       [btn-icon-component {:className (obj/get styles "extended-icon")}]
       btn-title]]]
    children)))

(defn component [props & children]
  [styled (assoc props :children children) component*])
