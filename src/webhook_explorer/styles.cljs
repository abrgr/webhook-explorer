(ns webhook-explorer.styles
  (:require [clojure.string :as string]
            [reagent.core :as r]
            ["@material-ui/core/styles" :as styles]
            [camel-snake-kebab.core :as csk]
            [camel-snake-kebab.extras :as cske]))

(defn- kebab-case-props [p]
  (cske/transform-keys csk/->kebab-case-keyword p))

(defn fix-special-syms [p]
  "Needed because react camel-casing a->b yields a>b, which isn't reversible"
  (->> p
       (mapcat
        (fn [[k v]]
          [[k v]
           [(-> k name (string/replace ">" "->") keyword) v]]))
       (into {})))

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
                                  (dissoc :wrapped)
                                  fix-special-syms
                                  kebab-case-props)])))
        wrapper (fn [props wrapped]
                  [:> react-component (assoc props :wrapped wrapped)])]
    wrapper))

(defn inject-css-link [href]
  (let [el (.createElement js/document "link")]
    (.setAttribute el "href" href)
    (.setAttribute el "rel" "stylesheet")
    (.appendChild (.-head js/document) el)))
