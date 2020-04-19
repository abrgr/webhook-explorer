(ns webhook-explorer.components.req-editor
  (:require [clojure.string :as string]
            [reagent.core :as r]
            [webhook-explorer.components.req-parts :as req-parts]
            [webhook-explorer.components.method-selector :as method-selector]
            [goog.object :as obj]
            ["@material-ui/core/FormControlLabel" :default FormControlLabel]
            ["@material-ui/core/FormControl" :default FormControl]
            ["@material-ui/core/Switch" :default Switch]
            ["@material-ui/core/TextField" :default TextField]))

(defn component [{:keys [protocol
                         method
                         host
                         path
                         qs
                         headers
                         body
                         on-update]}]
  [:<>
   [:> FormControl {:fullWidth true
                    :margin "normal"}
    [:> FormControlLabel {:label "Secure"
                          :control (r/as-element
                                    [:> Switch {:checked (= protocol "https")
                                                :onChange #(on-update
                                                            :protocol
                                                            (if (obj/getValueByKeys % #js ["target" "checked"])
                                                              "https"
                                                              "http"))}])}]]
   [method-selector/component
    {:value (string/lower-case (or  method ""))
     :on-change #(on-update :method %)}]
   [:> FormControl {:fullWidth true
                    :margin "normal"}
    [:> TextField {:fullWidth true
                   :label "Host"
                   :value (or host "")
                   :onChange #(on-update :host (obj/getValueByKeys % #js ["target" "value"]))}]]
   [:> FormControl {:fullWidth true
                    :margin "normal"}
    [:> TextField {:fullWidth true
                   :label "Path"
                   :value (or path "")
                   :onChange #(on-update :path (obj/getValueByKeys % #js ["target" "value"]))}]]
   [req-parts/editable-qs-view
    "Query Params"
    qs
    (fn [k v]
      (if (nil? v)
        (on-update :qs (dissoc qs k))
        (on-update :qs (assoc qs k v))))]
   [req-parts/editable-headers-view
    "Request Headers"
    headers
    (fn [k v]
      (if (nil? v)
        (on-update :headers (dissoc headers k))
        (on-update :headers k v)))]
   [req-parts/editable-body-view
    "Request Body"
    (req-parts/make-bodies
     {:raw {:label "Raw" :body body}})
    headers
    #(on-update :body %)]])
