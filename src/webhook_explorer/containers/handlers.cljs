(ns webhook-explorer.containers.handlers
  (:require [reagent.core :as r]
            [goog.object :as obj]
            [webhook-explorer.styles :as styles]
            ["@material-ui/core/Select" :default Select]
            ["@material-ui/core/MenuItem" :default MenuItem]
            ["@material-ui/core/InputLabel" :default InputLabel]
            ["@material-ui/core/Divider" :default Divider]
            ["@material-ui/core/Typography" :default Typography]
            ["@material-ui/core/Fab" :default Fab]
            ["@material-ui/core/Paper" :default Paper]
            ["@material-ui/core/Radio" :default Radio]
            ["@material-ui/core/RadioGroup" :default RadioGroup]
            ["@material-ui/core/FormControl" :default FormControl]
            ["@material-ui/core/FormControlLabel" :default FormControlLabel]
            ["@material-ui/core/FormLabel" :default FormLabel]
            ["@material-ui/core/TextField" :default TextField]
            ["@material-ui/icons/Add" :default AddIcon]))

(def ^:private styled
  (styles/style-wrapper
    (fn [theme]
      {:flex-container {:display "flex"
                        :align-items "center"}
       :container {:width "80%"
                   :height "100%"
                   :minWidth "480px"
                   :maxWidth "768px"
                   :margin "25px auto"}
       :path-container {:display "flex"
                        :alignItems "flex-start"}
       :full-flex {:flex 1
                   :marginLeft 20}
       :divider {:margin-top 16
                 :margin-bottom 16}
       :2-col-container {:display "flex"}
       :left-container {:width 100}
       :matcher-container {:marginTop 48
                           :padding 20}
       :add-matcher-container {:display "flex"
                               :flexDirection "column"
                               :alignItems "center"}})))

(def ^:private match-types
  (sorted-map
    "exact" {:label "Exact match"}
    "prefix" {:label "Prefix match"}))

(def ^:private response-types
  (sorted-map
    "mock-response" {:label "Mock response"}
    "proxied-response" {:label "Proxied response"}))

(defn- match-type-label [{:keys [value label]}]
  [:> FormControlLabel {:label label
                        :value value
                        :control (r/as-element [:> Radio])}])

(defn- path-component [{:keys [styles match-type path on-update]}]
  [:div {:className (obj/get styles "path-container")}
    [:> FormControl {:component "fieldset"
                     :margin "normal"}
      [:> FormLabel {:component "legend"}
        "Path match type"]
      [:> RadioGroup {:aria-label "Path match type"
                      :value match-type
                      :onChange (partial on-update :match-type)}
        (for [[match-type {:keys [label]}] match-types]
          ^{:key match-type}
          [match-type-label {:label label
                             :value match-type}])]]
    [:> FormControl {:margin "normal"
                     :className (obj/get styles "full-flex")}
      [:> TextField {:label "Path"
                     :helperText (r/as-element
                                   [:<>
                                     [:span "Exact match against '/the/{path}/here' matches '/the/path/here', '/the/other-path/here', etc."]
                                     [:br]
                                     [:span "Prefix match against '/the/{path}/here' matches '/the/path/here', '/the/other-path/here/and/here', etc."]])
                     :value path
                     :onChange (partial on-update :path)}]]])

(defn- matcher [{:keys [styles response-type on-update]}]
  [:> Paper {:elevation 3
             :className (obj/get styles "matcher-container")}
    [:div {:className (obj/get styles "2-col-container")}
      [:div {:className (obj/get styles "left-container")}
        [:> Typography {:variant "h5"
                        :color "textSecondary"}
          "When:"]]
      [:div {:className (obj/get styles "full-flex")}
        "yo"]]
    [:> Divider {:className (obj/get styles "divider")}]
    [:div {:className (obj/get styles "2-col-container")}
      [:div {:className (obj/get styles "left-container")}
        [:> Typography {:variant "h5"
                        :color "textSecondary"}
          "Then:"]]
      [:div {:className (obj/get styles "full-flex")}
        [:> FormControl {:fullWidth true
                         :margin "normal"}
          [:> InputLabel "Respond with"]
          [:> Select {:value response-type
                      :onChange (partial on-update :response-type)}
            (for [[rt {:keys [label]}] response-types]
              ^{:key rt}
              [(r/adapt-react-class MenuItem) {:value rt} label])]]]]])

(defn- -component []
  (let [v (r/atom {:match-type "exact"
                   :path ""
                   :response-type ""})
        on-update (fn [k evt]
                    (swap! v assoc k (obj/getValueByKeys evt #js ["target" "value"])))]
    (fn [{:keys [styles]}]
      (let [{:keys [match-type path response-type]} @v]
        [:div {:className (obj/get styles "container")}
          [path-component {:styles styles
                           :match-type match-type
                           :path path
                           :on-update on-update}]
          [matcher {:styles styles
                    :response-type response-type
                    :on-update on-update}]
          [:> Paper {:elevation 3
                     :className (str
                                  (obj/get styles "add-matcher-container")
                                  " "
                                  (obj/get styles "matcher-container"))}
            [:> Fab {:color "primary"}
              [:> AddIcon]]
            [:> Typography {:color "textSecondary"}
              "Add a matcher."]]]))))

(defn component []
  [styled {} -component])
