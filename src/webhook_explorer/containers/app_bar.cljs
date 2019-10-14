(ns webhook-explorer.containers.app-bar
  (:require [reagent.core :as r]
            ["@material-ui/core/styles" :as styles]
            ["@material-ui/core/AppBar" :default AppBar]
            ["@material-ui/core/Button" :default Button]
            ["@material-ui/core/Toolbar" :default Toolbar]
            ["@material-ui/core/IconButton" :default IconButton]
            ["@material-ui/icons/Menu" :default MenuIcon]
            ["@material-ui/core/Typography" :default Typography]
            [webhook-explorer.app-state :as app-state]
            [webhook-explorer.actions.auth :as auth-actions]))

(def ^:private styles
  (styles/makeStyles
    (fn [theme]
      (clj->js {:title {:flexGrow 1}
                :menu-btn {:marginRight (.spacing theme 2)}}))))

(defn- -component []
  (let [s (styles)]
    (r/as-element
      [:> AppBar {:position "static"}
        [:> Toolbar nil
          [:> IconButton {:edge "start"
                          :className ""
                          :color "inherit"
                          :aria-label "menu"}
            [:> MenuIcon]]
          [:> Typography {:variant "h6" :className (.-title s)} "Webhook Explorer"]
          (when-not (app-state/logged-in?)
            [:> Button {:color "inherit" :on-click #(auth-actions/sign-in)} "Login"])]])))

(defn component []
  [:> -component])
