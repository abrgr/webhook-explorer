(ns webhook-explorer.styles
  (:require [clojure.string :as string]
            [reagent.core :as r]
            ["@material-ui/core/styles" :as styles]))

(defn style-wrapper [class-defs]
  (let [make-styles (styles/makeStyles
                     (fn [theme]
                       (clj->js (if (fn? class-defs)
                                  (class-defs theme)
                                  class-defs))))
        wrapper (fn [props wrapped]
                  (let [react-component
                        (fn [js-props]
                          (let [s (make-styles)]
                            (r/as-element
                             [wrapped (assoc props :styles s)])))]
                    [:> react-component props]))]
    wrapper))

(defn inject-css-link [href]
  (let [el (.createElement js/document "link")]
    (.setAttribute el "href" href)
    (.setAttribute el "rel" "stylesheet")
    (.appendChild (.-head js/document) el)))
