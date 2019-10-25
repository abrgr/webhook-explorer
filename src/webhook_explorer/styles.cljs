(ns webhook-explorer.styles
  (:require [reagent.core :as r]
            ["@material-ui/core/styles" :as styles]))

(defn style-wrapper [class-defs]
  (let [make-styles (styles/makeStyles
                      (fn [theme]
                        (clj->js (if (fn? class-defs)
                                     (class-defs theme)
                                     class-defs))))
        react-component (fn [js-props]
                          (let [s (make-styles)
                                {:keys [wrapped] :as props} (js->clj js-props :keywordize-keys true)]
                            (r/as-element
                              [wrapped
                                (-> props
                                    (assoc :styles s)
                                    (dissoc :wrapped))])))
        wrapper (fn [props wrapped]
                  [:> react-component (assoc props :wrapped wrapped)])]
    wrapper))
