(ns webhook-explorer.containers.app-bar
  (:require [reagent.core :as r]
            ["@material-ui/core/styles" :as styles]
            ["@material-ui/core/AppBar" :default AppBar]
            ["@material-ui/core/Button" :default Button]
            ["@material-ui/core/Toolbar" :default Toolbar]
            ["@material-ui/core/IconButton" :default IconButton]
            ["@material-ui/icons/Menu" :default MenuIcon]
            ["@material-ui/core/Typography" :default Typography]
            ["@material-ui/core/Avatar" :default Avatar]
            [webhook-explorer.app-state :as app-state]
            [webhook-explorer.actions.auth :as auth-actions]))

(def ^:private styles
  (styles/makeStyles
    (fn [theme]
      (clj->js {:title {:flexGrow 1}
                :menu-btn {:marginRight (.spacing theme 2)}}))))

(defn- avatar []
  (let [{:keys [given-name family-name name pic-url]} (app-state/user-info)]
    [:> Avatar {:alt name
                :src pic-url}
      (when (nil? pic-url)
        (str (first given-name) (first family-name)))]))

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
          (if (app-state/logged-in?)
            [avatar]
            [:> Button {:color "inherit" :on-click #(auth-actions/sign-in)} "Login"])]])))

(defn component []
  [:> -component])
