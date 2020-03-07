(ns webhook-explorer.components.method-selector
  (:require [goog.object :as obj]
            ["@material-ui/core/FormControlLabel" :default FormControlLabel]
            ["@material-ui/core/FormControl" :default FormControl]
            ["@material-ui/core/InputLabel" :default InputLabel]
            ["@material-ui/core/Select" :default Select]
            ["@material-ui/core/MenuItem" :default MenuItem]))

(defn component [{:keys [value on-change class-name]}]
  [:> FormControl {:fullWidth true
                   :margin "normal"
                   :className class-name}
   [:> InputLabel "Method"]
   [:> Select {:value (or value "")
               :onChange #(on-change (obj/getValueByKeys % #js ["target" "value"]))}
    [:> MenuItem {:value "get"} "GET"]
    [:> MenuItem {:value "post"} "POST"]
    [:> MenuItem {:value "put"} "PUT"]
    [:> MenuItem {:value "patch"} "PATCH"]
    [:> MenuItem {:value "delete"} "DELETE"]
    [:> MenuItem {:value "options"} "OPTIONS"]]])
