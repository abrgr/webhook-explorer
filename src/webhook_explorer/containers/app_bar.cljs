(ns webhook-explorer.containers.app-bar
  (:require [reagent.core :as r]
            [goog.object :as obj]
            ["@material-ui/core/AppBar" :default AppBar]
            ["@material-ui/core/Button" :default Button]
            ["@material-ui/core/Toolbar" :default Toolbar]
            ["@material-ui/core/IconButton" :default IconButton]
            ["@material-ui/core/Menu" :default Menu]
            ["@material-ui/core/MenuItem" :default MenuItem]
            ["@material-ui/core/List" :default List]
            ["@material-ui/core/ListItem" :default ListItem]
            ["@material-ui/core/ListItemIcon" :default ListItemIcon]
            ["@material-ui/core/ListItemText" :default ListItemText]
            ["@material-ui/core/ListSubheader" :default ListSubheader]
            ["@material-ui/icons/Menu" :default MenuIcon]
            ["@material-ui/icons/Dashboard" :default RequestsIcon]
            ["@material-ui/icons/Group" :default UsersIcon]
            ["@material-ui/icons/SettingsInputComponent" :default HandlerConfigIcon]
            ["@material-ui/core/Drawer" :default Drawer]
            ["@material-ui/core/Typography" :default Typography]
            ["@material-ui/core/Avatar" :default Avatar]
            [webhook-explorer.app-state :as app-state]
            [webhook-explorer.styles :as styles]
            [webhook-explorer.routes :as routes]
            [webhook-explorer.actions.auth :as auth-actions]))

(def ^:private styled
  (styles/style-wrapper
    (fn [theme]
      {:title {:flexGrow 1}
       :menu-btn {:marginRight (.spacing theme 2)}
       :app-bar {:zIndex (inc (obj/getValueByKeys theme #js ["zIndex" "modal"]))}
       :toolbar-offset (obj/getValueByKeys theme #js ["mixins" "toolbar"])
       :drawer {:width 200}})))

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
  (let [drawer-open (r/atom false)
        nav-from-drawer (fn [f] (f) (reset! drawer-open false))]
    (fn []
      [:<>
        [:> AppBar {:position "fixed"
                    :className (obj/get styles "app-bar")}
          [:> Toolbar
            (when (app-state/logged-in?)
              [:> IconButton {:edge "start"
                              :className ""
                              :color "inherit"
                              :aria-label "menu"
                              :onClick #(swap! drawer-open not)}
                [:> MenuIcon]])
            [:> Typography {:variant "h6" :className (obj/get styles "title")} "Webhook Explorer"]
            (if (app-state/logged-in?)
              [avatar]
              [:> Button {:color "inherit" :on-click #(auth-actions/sign-in)} "Login"])]]
        (when (app-state/logged-in?)
          [:> Drawer
            {:variant "temporary"
             :classes #js {:paper (obj/get styles "drawer")}
             :open @drawer-open
             :onClose #(reset! drawer-open false)}
            [:div {:className (obj/get styles "toolbar-offset")}]
            [:> List
              [:> ListItem {:button true
                            :onClick #(nav-from-drawer routes/nav-to-reqs)}
                [:> ListItemIcon
                  [:> RequestsIcon]]
                [:> ListItemText
                  "Requests"]]
              [:> ListItem {:button true
                            :onClick #(nav-from-drawer routes/nav-to-handlers)}
                [:> ListItemIcon
                  [:> HandlerConfigIcon]]
                [:> ListItemText
                  "Handlers"]]
              [:> ListItem {:button true
                            :onClick #(nav-from-drawer routes/nav-to-users)}
                [:> ListItemIcon
                  [:> UsersIcon]]
                [:> ListItemText
                  "Users"]]]])
        [:div {:className (obj/get styles "toolbar-offset")}]])))

(defn component []
  [styled {} -component])
