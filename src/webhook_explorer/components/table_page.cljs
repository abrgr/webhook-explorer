(ns webhook-explorer.components.table-page
  (:require [webhook-explorer.styles :as styles]
            [webhook-explorer.components.infinite-table :as infinite-table]
            [goog.object :as obj]
            ["@material-ui/core/Button" :default Button]))

(def ^:private styled
  (styles/style-wrapper
   (fn [theme]
     {:flex-container {:display "flex"
                       :align-items "center"}
      :no-outline {:outline "none"}
      :container {:width "80%"
                  :height "100%"
                  :minWidth "480px"
                  :maxWidth "768px"
                  :margin "25px auto"}
      :right-align {:width "100%"
                    :display "flex"
                    :justify-content "flex-end"}})))

(defn- -component [{:keys [styles
                           create-btn-content
                           on-create
                           row-height
                           items
                           next-req
                           load-more-items
                           get-row-by-idx
                           cols
                           no-rows-renderer
                           cell-renderer]}]
  [:div {:className (obj/get styles "container")}
   (when create-btn-content
     [:div {:className (obj/get styles "right-align")}
      [:> Button {:variant "contained"
                  :color "primary"
                  :onClick on-create}
       create-btn-content]])
   [infinite-table/component
    {:row-height row-height
     :items items
     :next-req next-req
     :load-more-items load-more-items
     :get-row-by-idx get-row-by-idx
     :cols cols
     :no-rows-renderer no-rows-renderer
     :cell-renderer cell-renderer}]])

(defn component [props]
  [styled props -component])
