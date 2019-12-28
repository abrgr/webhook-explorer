(ns webhook-explorer.containers.app-bar
  (:require [reagent.core :as r]
            [goog.object :as obj]
            ["@material-ui/core/AppBar" :default AppBar]
            ["@material-ui/core/Button" :default Button]
            ["@material-ui/core/Toolbar" :default Toolbar]
            ["@material-ui/core/IconButton" :default IconButton]
            ["@material-ui/core/Menu" :default Menu]
            ["@material-ui/core/MenuItem" :default MenuItem]
            ["@material-ui/core/ListSubheader" :default ListSubheader]
            ["@material-ui/icons/Menu" :default MenuIcon]
            ["@material-ui/core/Typography" :default Typography]
            ["@material-ui/core/Avatar" :default Avatar]
            [webhook-explorer.app-state :as app-state]
            [webhook-explorer.styles :as styles]
            [webhook-explorer.actions.auth :as auth-actions]))

(def ^:private styled
  (styles/style-wrapper
    (fn [theme]
      {:title {:flexGrow 1}
       :menu-btn {:marginRight (.spacing theme 2)}
       :app-bar-offset (js->clj (obj/getValueByKeys theme #js ["mixins" "toolbar"]) :keywordize-keys true)})))

(defn- avatar []
  (let [anchor-el (r/atom nil)]
    (fn []
      (let [{:keys [given-name family-name name email pic-url] :as u} (app-state/user-info)
            anchor @anchor-el]
        [:<>
          [:> Avatar {:alt name
                      :src pic-url
                      :onClick #(reset! anchor-el (obj/get % "currentTarget"))}
            (when (nil? pic-url)
              (str (first given-name) (first family-name)))]
          [:> Menu {:anchorEl anchor
                    :anchorOrigin #js {:vertical "bottom" :horizontal "left"}
                    :getContentAnchorEl nil
                    :open (some? anchor)
                    :onClose #(reset! anchor-el nil)}
            [:> ListSubheader email]
            [:> MenuItem {:onClick #(auth-actions/sign-out)}
              "Log Out"]]]))))

(defn- -component [{:keys [styles]}]
  [:<>
    [:> AppBar {:position "fixed"}
      [:> Toolbar nil
        [:> IconButton {:edge "start"
                        :className ""
                        :color "inherit"
                        :aria-label "menu"}
          [:> MenuIcon]]
        [:> Typography {:variant "h6" :className (.-title styles)} "Webhook Explorer"]
        (if (app-state/logged-in?)
          [avatar]
          [:> Button {:color "inherit" :on-click #(auth-actions/sign-in)} "Login"])]]
    [:div {:className (obj/get styles "app-bar-offset")}]])

(defn component []
  [styled {} -component])
