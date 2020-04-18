(ns webhook-explorer.components.req-captures
  (:require [reagent.core :as r]
            [goog.object :as obj]
            [webhook-explorer.styles :as styles]
            [webhook-explorer.components.req-parts :as req-parts]
            ["@material-ui/core/Paper" :default Paper]
            ["@material-ui/core/Typography" :default Typography]
            ["@material-ui/core/FormControl" :default FormControl]
            ["@material-ui/core/InputLabel" :default InputLabel]
            ["@material-ui/core/Select" :default Select]
            ["@material-ui/core/MenuItem" :default MenuItem]))

(def ^:private styled
  (styles/style-wrapper
   (fn [theme]
     {:full-flex {:flex 1
                  :marginLeft 20}
      :capture-container {:marginTop 20
                          :padding 20
                          "& .MuiExpansionPanelSummary-root" {:padding 0}}})))

(defn template-var-map->simple-map [m]
  (some->> m
           (map (fn [[k {:keys [template-var]}]] [k template-var]))
           (into {})))

(def ^:private body-capture-types
  (array-map
   :json {:label "JSON"
          :title "JSON path to capture"}
   :form-data {:label "Form Data"
               :title "Form field to capture"}))

(defn- component* [{:keys [styles
                           header-captures
                           body-capture-type
                           body-captures
                           on-update-header-capture
                           on-remove-header-capture
                           on-remove-all-body-captures
                           on-update-body-capture-type
                           on-update-body-capture
                           on-remove-body-capture]}]
  [:> Paper {:elevation 3
             :className (obj/get styles "capture-container")}
   [:> Typography {:variant "h6"
                   :color "textSecondary"}
    "Capture Template Variables"]
   [:div {:className (obj/get styles "full-flex")}
    [req-parts/editable-headers-view
     (str "Request header captures (" (count header-captures) ")")
     header-captures
     (fn [k v]
       (if (nil? v)
         (on-remove-header-capture (name k))
         (on-update-header-capture (name k) v)))]
    [:> FormControl {:fullWidth true
                     :margin "normal"}
     [:> InputLabel "Request body captures"]
     [:> Select {:value (if body-capture-type (name body-capture-type) " ")
                 :onChange #(let [v (obj/getValueByKeys % #js ["target" "value"])]
                              (if (= v " ")
                                (on-remove-all-body-captures)
                                (on-update-body-capture-type (keyword v))))}
      [:> MenuItem {:value " "} "None"]
      (for [[mt {:keys [label]}] body-capture-types]
        ^{:key (name mt)}
        [(r/adapt-react-class MenuItem) {:value (name mt)} label])]]
    (when (some? body-captures)
      [req-parts/base-kv-view
       {:title "Body captures"
        :k-title (some-> body-capture-type body-capture-types :title)
        :v-title "Template variable"
        :m body-captures
        :editable true
        :value-component req-parts/editable-value
        :default-expanded true
        :on-change (fn [jp v]
                     (if (nil? v)
                       (on-remove-body-capture (name jp))
                       (on-update-body-capture (name jp) v)))}])]])

(defn component [props]
  [styled props component*])
