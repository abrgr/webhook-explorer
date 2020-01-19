(ns webhook-explorer.containers.handlers
  (:require [reagent.core :as r]
            [goog.object :as obj]
            [webhook-explorer.styles :as styles]
            [webhook-explorer.components.req-parts :as req-parts]
            ["@material-ui/core/Chip" :default Chip]
            ["@material-ui/core/IconButton" :default IconButton]
            ["@material-ui/icons/ArrowDownward" :default DownArrowIcon]
            ["@material-ui/icons/ArrowUpward" :default UpArrowIcon]
            ["@material-ui/core/Select" :default Select]
            ["@material-ui/core/MenuItem" :default MenuItem]
            ["@material-ui/core/InputLabel" :default InputLabel]
            ["@material-ui/core/Divider" :default Divider]
            ["@material-ui/core/Typography" :default Typography]
            ["@material-ui/core/Tooltip" :default Tooltip]
            ["@material-ui/core/Fab" :default Fab]
            ["@material-ui/core/Paper" :default Paper]
            ["@material-ui/core/Radio" :default Radio]
            ["@material-ui/core/RadioGroup" :default RadioGroup]
            ["@material-ui/core/FormControl" :default FormControl]
            ["@material-ui/core/FormControlLabel" :default FormControlLabel]
            ["@material-ui/core/FormLabel" :default FormLabel]
            ["@material-ui/core/TextField" :default TextField]
            ["@material-ui/icons/Publish" :default SaveIcon]
            ["@material-ui/icons/Add" :default AddIcon]))

(def ^:private styled
  (styles/style-wrapper
    (fn [theme]
      {:flex-container {:display "flex"
                        :align-items "center"}
       :extended-icon {:marginRight (.spacing theme 1)}
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
       :2-col-container {:display "flex"
                         "& .MuiExpansionPanelSummary-root" {:padding 0}}
       :right-controls {:display "flex"
                        :flex-direction "row"
                        :justify-content "flex-end"}
       :left-container {:width 100}
       :caption-container {:position "relative"}
       :caption {:position "absolute"
                 :bottom -48}
       :capture-container {:marginTop 20
                           :padding 20
                           "& .MuiExpansionPanelSummary-root" {:padding 0}}
       :template-var-container {:flex 1}
       :publish-container {:margin "auto"}
       :bottom-container {:position "fixed"
                          :display "flex"
                          :left 0
                          :right 0
                          :bottom 0
                          :height 150
                          :border-top "2px solid #eee"
                          :z-index 100
                          :padding 20}
       :matcher-container {:marginTop 48
                           :padding 20}
       :add-matcher-container {:display "flex"
                               :flexDirection "column"
                               :alignItems "center"}})))

(def ^:private match-types
  (array-map
    :exact {:label "Exact match"}
    :prefix {:label "Prefix match"}))

(def ^:private handler-types
  (array-map
    :mock {:label "Mock"}
    :proxy {:label "Proxy"}))

(def ^:private body-capture-types
  (array-map
    :json {:label "JSON"}
    :form-data {:label "Form Data"}))

(defn- get-target-value [evt]
  (obj/getValueByKeys evt #js ["target" "value"]))

(defmulti handler-component (fn [{{:keys [type]} :handler}] type))

(defmethod handler-component :default [_]
  [:div])

(defmethod handler-component :mock [{{:keys [mock]} :handler}]
  [:div "mock"])

(defmethod handler-component :proxy [{{:keys [proxy]} :handler :keys [idx on-update]}]
  (let [{:keys [remote-url]} proxy]
    [:> TextField
      {:label "Remote URL"
       :fullWidth true
       :helperText "URL to proxy matching requests to"
       :value (or remote-url "")
       :onChange #(on-update assoc-in [:matchers idx :handler :proxy :remote-url] (get-target-value %))}]))

(defn- path-component [{:keys [styles match-type path on-update]}]
  [:div {:className (obj/get styles "path-container")}
    [:> FormControl {:component "fieldset"
                     :margin "normal"}
      [:> FormLabel {:component "legend"}
        "Path match type"]
      [:> RadioGroup {:aria-label "Path match type"
                      :value (if match-type (name match-type) "")
                      :onChange #(on-update assoc :match-type (keyword (get-target-value %)))}
        (for [[match-type {:keys [label]}] match-types]
          ^{:key match-type}
          [(r/adapt-react-class FormControlLabel)
            {:label label
             :value (name match-type)
             :control (r/as-element [:> Radio])}])]]
    [:> FormControl {:margin "normal"
                     :className (obj/get styles "full-flex")}
      [:> TextField {:label "Path"
                     :helperText (r/as-element
                                   [:<>
                                     [:span "Exact match against '/the/{path}/here' matches '/the/path/here', '/the/other-path/here', etc."]
                                     [:br]
                                     [:span "Prefix match against '/the/{path}/here' matches '/the/path/here', '/the/other-path/here/and/here', etc."]])
                     :value path
                     :onChange #(on-update assoc :path (get-target-value %))}]]])

(defn- move-item [idx dir v]
  (let [op (case dir
             :up dec
             :down inc)]
    (assoc v
      idx (get v (op idx))
      (op idx) (get v idx))))

(defn- path->template-vars [path]
  (->> (re-seq #"[{]([^}]+)[}]" path)
       (map second)))

(defn- matcher [{:keys [styles idx total-matcher-count handler matches on-update]}]
  (let [on-update-input (fn [k evt]
                          (on-update assoc-in [:matchers idx k] (get-target-value evt)))
        handler-type (:type handler)]
    [:> Paper {:elevation 3
               :className (obj/get styles "matcher-container")}
      [:div {:className (obj/get styles "right-controls")}
        [:> IconButton {:disabled (= idx 0)
                        :onClick #(on-update
                                    update
                                    :matchers 
                                    (partial move-item idx :up))}
          [:> UpArrowIcon]]
        [:> IconButton {:disabled (= idx (dec total-matcher-count))
                        :onClick #(on-update
                                    update
                                    :matchers 
                                    (partial move-item idx :down))}
          [:> DownArrowIcon]]]
      [:div {:className (obj/get styles "2-col-container")}
        [:div {:className (obj/get styles "left-container")}
          [:> Typography {:variant "h5"
                          :color "textSecondary"}
            "When:"]]
        [:div {:className (obj/get styles "full-flex")}
          [:> FormControl {:fullWidth true
                           :margin "normal"}
            [:> InputLabel "Request body matcher"]
            [req-parts/base-kv-view
              (str "Template variable matches (" (count matches) ")")
              "Template variable to check"
              "Matched value"
              matches
              true
              (fn [])
              req-parts/editable-value
              false
              (fn [tv v]
                (if (nil? v)
                  (on-update update-in [:matchers idx :matches] dissoc tv)
                  (on-update assoc-in [:matchers idx :matches tv] v)))]]]]
      [:> Divider {:className (obj/get styles "divider")}]
      [:div {:className (obj/get styles "2-col-container")}
        [:div {:className (obj/get styles "left-container")}
          [:> Typography {:variant "h5"
                          :color "textSecondary"}
            "Then:"]]
        [:div {:className (obj/get styles "full-flex")}
          [:> FormControl {:fullWidth true
                           :margin "normal"}
            [:> InputLabel "Handle with"]
            [:> Select {:value (if handler-type (name handler-type) "")
                        :onChange #(on-update assoc-in [:matchers idx :handler :type] (keyword (get-target-value %)))}
              (for [[rt {:keys [label]}] handler-types]
                ^{:key rt}
                [(r/adapt-react-class MenuItem) {:value (name rt)} label])]]
          [handler-component {:handler handler
                              :idx idx
                              :on-update on-update
                              :styles styles}]]]]))

(def ^:private new-matcher-template
  {:proto :https
   :matches {}
   :handler nil})

(defn- template-var-map->simple-map [m]
  (->> m
       (map (fn [[k {:keys [template-var]}]] [k template-var]))
       (into {})))
  
(defn- captures [{:keys [styles path header-captures body-capture-type body-captures on-update]}]
  [:> Paper {:elevation 3
             :className (obj/get styles "capture-container")}
    [:> Typography {:variant "h6"
                    :color "textSecondary"}
      "Capture Template Variables"]
    [:div {:className (obj/get styles "full-flex")}
      [req-parts/editable-headers-view
        (str "Request header captures (" (count header-captures) ")")
        (template-var-map->simple-map header-captures)
        (fn [k v]
          (if (nil? v)
            (on-update update-in [:captures :headers] dissoc k) 
            (on-update assoc-in [:captures :headers k :template-var] v)))]
      [:> FormControl {:fullWidth true
                       :margin "normal"}
        [:> InputLabel "Request body captures"]
        [:> Select {:value (if body-capture-type (name body-capture-type) " ")
                    :onChange #(let [v (get-target-value %)]
                                 (if (= v " ")
                                   (on-update update :captures dissoc :body)
                                   (on-update assoc-in [:captures :body] {:type (keyword v) :captures {}})))}
          [:> MenuItem {:value " "} "None"]
          (for [[mt {:keys [label]}] body-capture-types]
            ^{:key mt}
            [(r/adapt-react-class MenuItem) {:value (name mt)} label])]]
      (when (some? body-captures)
        [req-parts/base-kv-view
          "Body captures"
          "JSON Path to capture"
          "Template variable"
          (template-var-map->simple-map body-captures)
          true
          (fn [])
          req-parts/editable-value
          true
          (fn [jp v]
            (if (nil? v)
              (on-update update-in [:captures :body :captures] dissoc jp)
              (on-update assoc-in [:captures :body :captures jp :template-var] v)))])]])

(defn- template-var-container [{:keys [styles path header-captures body-captures]}]
  (let [vars (->> (concat (vals header-captures) (vals body-captures))
                  (map :template-var)
                  (concat (path->template-vars path)))]
    [:div {:className (obj/get styles "template-var-container")}
      [:> Typography {:variant "h6"
                      :color "textSecondary"}
        "Captured template variables"]
      (if (empty? vars)
        [:div {:style #js {:margin "auto"}}
          "No captured variables yet"]
        (for [tv vars]
          ^{:key tv}
          [(r/adapt-react-class Chip) {:label tv
                                       :variant "outlined"}]))]))

(defn- -component []
  (let [state (r/atom {:match-type "exact"
                       :path ""
                       :matchers []})
        on-update (fn [& updater]
                    (apply swap! state updater))]
    (fn [{:keys [styles]}]
      (let [{:keys [match-type path matchers]
             {header-captures :headers
              {body-capture-type :type
               body-captures :captures} :body} :captures} @state]
        [:div {:className (obj/get styles "container")}
          [:> Paper {:className (obj/get styles "bottom-container")}
            [template-var-container {:styles styles
                                     :path path
                                     :header-captures header-captures
                                     :body-captures body-captures}]
            [:div {:className (obj/get styles "publish-container")}
              [:> Fab {:variant "extended"
                       :label "Save"
                       :color "secondary"
                       :onClick #(println @state)}
                [:> SaveIcon {:className (obj/get styles "extended-icon")}]
                "Publish changes"]]]
          [path-component {:styles styles
                           :match-type match-type
                           :path path
                           :on-update on-update}]
          [captures {:styles styles
                     :path path
                     :header-captures header-captures
                     :body-capture-type body-capture-type
                     :body-captures body-captures
                     :on-update on-update}]
          [:div {:className (obj/get styles "caption-container")}
            [:> Typography {:variant "caption"
                            :className (obj/get styles "caption")}
            "Matchers are processed in order, top to bottom. First match wins."]]
          (map-indexed
            (fn [idx {:keys [match-type path matches handler]}]
              ^{:key idx}
              [matcher {:styles styles
                        :idx idx
                        :total-matcher-count (count matchers)
                        :handler handler
                        :matches matches
                        :on-update on-update}])
            matchers)
          [:> Paper {:elevation 3
                     :className (str
                                  (obj/get styles "add-matcher-container")
                                  " "
                                  (obj/get styles "matcher-container"))}
            [:> Fab {:color "primary"
                     :onClick #(on-update update :matchers conj new-matcher-template)}
              [:> AddIcon]]
            [:> Typography {:color "textSecondary"}
              "Add a matcher."]]]))))

(defn component []
  [styled {} -component])
