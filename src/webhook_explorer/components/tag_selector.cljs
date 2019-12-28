(ns webhook-explorer.components.tag-selector
  (:require [reagent.core :as r]
            [goog.object :as obj]
            [webhook-explorer.app-state :as app-state]
            [clojure.string :as string]
            ["@material-ui/core/Menu" :default Menu]
            ["@material-ui/core/MenuItem" :default MenuItem]
            ["@material-ui/core/ListSubheader" :default ListSubheader]
            ["@material-ui/core/TextField" :default TextField]
            ["@material-ui/core/Typography" :default Typography]))

(defn- filter-tags [input tags]
  (if (zero? (count input))
    tags
    (filter
      #(string/includes? (string/lower-case %) (string/lower-case input))
      tags)))

(defn component []
  (let [tag-anchor-el (r/atom nil)
        input-tag (r/atom "")]
    (fn [{:keys [target-component on-select-tag rw allow-creation extra-tags selected-label]
          cur-private-tags :private-tags
          cur-public-tags :public-tags
          :or {cur-private-tags #{}
               cur-public-tags #{}}}]
      (let [entered-tag @input-tag
            el @tag-anchor-el
            tags @app-state/tags
            extra-tag-labels (set (filter-tags entered-tag (vals extra-tags)))
            private-tags (filter-tags entered-tag (:user tags))
            public-tags (filter-tags entered-tag (get-in tags [:public rw]))
            close-menu (fn []
                         (reset! input-tag "")
                         (reset! tag-anchor-el nil))
            apply-tag (fn [tag-opt]
                        (on-select-tag tag-opt)
                        (close-menu))
            is-entered-tag? #(-> %
                                 (string/lower-case)
                                 (= (string/lower-case entered-tag)))
            new-private (when-not (or (empty? entered-tag)
                                      (some is-entered-tag? private-tags))
                          entered-tag)
            new-public (when-not (or (empty? entered-tag)
                                      (some is-entered-tag? public-tags))
                          entered-tag)]
        [:<>
          [target-component
            {:on-open-menu #(reset! tag-anchor-el (obj/get % "currentTarget"))
             :extra-tags extra-tags
             :selected-priv-tags cur-private-tags
             :selected-pub-tags cur-public-tags
             :any-selected (or (not-empty cur-public-tags) (not-empty cur-private-tags))}]
          [:> Menu {:anchorEl el
                    :open (some? el)
                    :onClose #(reset! tag-anchor-el nil)
                    :getContentAnchorEl nil
                    :anchorOrigin #js {:vertical "bottom"
                                       :horizontal "left"}}
            [:> MenuItem {}
              [:> TextField {:fullWidth true
                             :label "Tag"
                             :value entered-tag
                             :onKeyDown #(when-not (string/starts-with? (obj/get % "key") "Arrow")
                                           (.stopPropagation %))
                             :onChange #(reset! input-tag (obj/getValueByKeys % #js ["target" "value"]))}]]
            (for [[priv-tag tag-name] extra-tags
                  :when (contains? extra-tag-labels tag-name)
                  :let [tagged (contains? cur-private-tags priv-tag)]]
              ^{:key priv-tag}
              [:> MenuItem {:onClick #(if tagged (close-menu) (apply-tag {:tag priv-tag}))}
                [:> Typography {:color (if tagged "primary" "textPrimary")}
                  (str tag-name " " (when tagged selected-label))]])
            (when (not-empty private-tags)
              [:> ListSubheader "Private tags"])
            (for [tag private-tags
                  :let [tagged (contains? cur-private-tags tag)]]
              ^{:key tag}
              [:> MenuItem {:onClick #(if tagged (close-menu) (apply-tag {:tag tag}))}
                [:> Typography {:color (if tagged "primary" "textPrimary")}
                  (str tag " " (when tagged selected-label))]])
            (when (not-empty public-tags)
              [:> ListSubheader "Public tags"])
            (for [tag public-tags
                  :let [tagged (contains? cur-public-tags tag)]]
              ^{:key tag}
              [:> MenuItem {:onClick #(if tagged (close-menu) (apply-tag {:pub true :tag tag}))}
                [:> Typography {:color (if tagged "primary" "textPrimary")}
                  (str tag " " (when tagged selected-label))]])
              (when (and allow-creation (or new-private new-public))
                [:> ListSubheader "Create new tag"])
              (when (and allow-creation new-private)
                [:> MenuItem {:onClick #(apply-tag {:tag new-private})}
                  (str "New private tag \"" new-private "\"")])
              (when (and allow-creation new-public)
                [:> MenuItem {:onClick #(apply-tag {:pub true :tag new-public})}
                  (str "New public tag \"" new-public "\"")])]]))))

