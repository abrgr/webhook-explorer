(ns webhook-explorer.containers.app-bar
  (:require [reagent.core :as r]
            ["@material-ui/core/styles" :as styles]
            ["@material-ui/core/AppBar" :default AppBar]
            ["@material-ui/core/Toolbar" :default Toolbar]
            ["@material-ui/core/IconButton" :default IconButton]
            ["@material-ui/icons/Menu" :default MenuIcon]
            ["@material-ui/core/Typography" :default Typography]))

(def ^:private classes
  (js->clj
    (styles/makeStyles
      (fn [theme]
        (clj->js {:title {:flexGrow 1}
                  :menu-btn {:marginRight (.spacing theme 2)}})))))

(defn component []
  [:> AppBar {:position "static"}
    [:> Toolbar nil
      [:> IconButton {:edge "start"
                      :className ""
                      :color "inherit"
                      :aria-label "menu"}
        [:> MenuIcon]]
      [:> Typography {:variant "h6" :className (.-title classes)} "Webhook Explorer"]]])
