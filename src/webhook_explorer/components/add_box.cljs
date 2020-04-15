(ns webhook-explorer.components.add-box
  (:require [reagent.core :as r]
            [goog.object :as obj]
            [webhook-explorer.styles :as styles]
            ["@material-ui/core/Fab" :default Fab]
            ["@material-ui/core/Paper" :default Paper]
            ["@material-ui/core/Typography" :default Typography]
            ["@material-ui/icons/Add" :default AddIcon]))

(def ^:private styled
  (styles/style-wrapper
   (fn [theme]
     {:container {:display "flex"
                  :flexDirection "column"
                  :alignItems "center"}})))

(defn component* [{:keys [styles class-name on-click title]}]
  [:> Paper {:elevation 3
             :className (str (obj/get styles "container") " " class-name)}
   [:> Fab {:color "primary"
            :onClick on-click}
    [:> AddIcon]]
   [:> Typography {:color "textSecondary"}
    title]])

(defn component [props]
  [styled props component*])
