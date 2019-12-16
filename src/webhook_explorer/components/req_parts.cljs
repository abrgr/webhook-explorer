(ns webhook-explorer.components.req-parts
  (:require [reagent.core :as r]
            [webhook-explorer.init :as init]
            [webhook-explorer.styles :as styles]
            [goog.object :as obj]
            ["@material-ui/core/IconButton" :default IconButton]
            ["@material-ui/icons/Add" :default AddIcon]
            ["@material-ui/icons/Delete" :default DeleteIcon]
            ["@material-ui/core/ExpansionPanel" :default ExpansionPanel]
            ["@material-ui/core/ExpansionPanelSummary" :default ExpansionPanelSummary]
            ["@material-ui/icons/ExpandMore" :default ExpandMoreIcon]
            ["@material-ui/core/ExpansionPanelDetails" :default ExpansionPanelDetails]
            ["@material-ui/core/CircularProgress" :default CircularProgress]
            ["@material-ui/core/TextField" :default TextField]
            ["@material-ui/core/Table" :default Table]
            ["@material-ui/core/TableBody" :default TableBody]
            ["@material-ui/core/TableCell" :default TableCell]
            ["@material-ui/core/TableHead" :default TableHead]
            ["@material-ui/core/TableRow" :default TableRow]
            ["@material-ui/core/TableFooter" :default TableFooter]
            ["codemirror" :as CM]
            ["react-codemirror" :as CodeMirror]
            ["codemirror/mode/meta"]
            ["codemirror/mode/javascript/javascript"]
            ["codemirror/mode/xml/xml"]
            ["codemirror/mode/clojure/clojure"]
            ["codemirror/mode/yaml/yaml"]))

(defn- init! []
  (styles/inject-css-link "https://cdnjs.cloudflare.com/ajax/libs/codemirror/5.48.4/codemirror.css"))

(init/register-init 10 init!)

(def ^:private styled
  (styles/style-wrapper
    (fn [theme]
      {:code {:width "100%"
              "& > .CodeMirror" {:height "auto"
                                 :border "1px solid #eee"}}})))

(defn- editor [{:keys [value content-type onChange styles] :as p}]
  (let [mode (when-let [m (.findModeByMIME CM (or content-type "text/plain"))]
               (obj/get m "mode"))]
    (r/as-element
      [:> CodeMirror {:className (obj/get styles "code")
                      :value value
                      :onChange onChange
                      :options #js {:viewportMargin ##Inf
                                    :mode mode}}])))

(defn- styled-editor [value content-type on-change]
  [styled {:value value :content-type content-type :onChange on-change} editor])

(defn base-kv-view
  ([title k-title v-title m editable on-visibility-toggled value-component]
    [base-kv-view title k-title v-title m editable on-visibility-toggled value-component {}])
  ([title k-title v-title m editable on-visibility-toggled value-component on-change]
    (let [new-kv (r/atom {:new-k "" :new-v ""})]
      (fn [title k-title v-title m editable on-visibility-toggled value-component on-change]
        (let [{:keys [new-k new-v]} @new-kv]
          [:> ExpansionPanel {:elevation 0
                              :TransitionProps #js {:onEntered on-visibility-toggled
                                                    :onExit on-visibility-toggled
                                                    :unmountOnExit true}}
            [:> ExpansionPanelSummary {:expandIcon (r/as-element [:> ExpandMoreIcon])}
              title]
            [:> ExpansionPanelDetails
              (if (nil? m)
                [:> CircularProgress]
                [:> Table {:aria-label title}
                  [:> TableHead
                    [:> TableRow
                      (when editable
                        [:> TableCell ""])
                      [:> TableCell k-title]
                      [:> TableCell v-title]]]
                  [:> TableBody
                    (for [[k v] m]
                      ^{:key k}
                      [:> TableRow
                        (when editable
                          [:> TableCell
                            [:> IconButton {:aria-label "delete"
                                            :onClick #(on-change k nil)}
                              [:> DeleteIcon {:fontSize "small"}]]])
                        [:> TableCell k]
                        [:> TableCell [value-component {:value v :key k :on-change on-change}]]])]
                  (when editable
                    [:> TableFooter
                      [:> TableRow
                        [:> TableCell
                          [:> IconButton {:aria-label "add"
                                          :onClick #(do (on-change (keyword new-k) new-v)
                                                        (reset! new-kv {:new-k "" :new-v ""}))}
                            [:> AddIcon {:fontSize "small"}]]]
                        [:> TableCell
                          [:> TextField {:value new-k
                                         :onChange #(swap! new-kv assoc :new-k (obj/getValueByKeys % #js ["target" "value"]))}]]
                        [:> TableCell
                          [:> TextField {:value new-v
                                         :onChange #(swap! new-kv assoc :new-v (obj/getValueByKeys % #js ["target" "value"]))}]]]])])]])))))

(defn- base-value [{:keys [value]}]
  value)

(defn- nop [] nil)

(defn headers-view
  ([title headers]
    [headers-view title headers nop])
  ([title headers on-visibility-toggled]
    [base-kv-view title "Header" "Value" headers false on-visibility-toggled base-value]))

(defn- editable-value [{:keys [key value on-change]}]
  ^{:key key}
  [:> TextField {:value value
                 :onChange #(on-change key (obj/getValueByKeys % #js ["target" "value"]))}])

(defn editable-headers-view [title headers on-header-change]
  [base-kv-view title "Header" "Value" headers true nop editable-value on-header-change])

(defn editable-qs-view [title qs on-qs-change]
  [base-kv-view title "Key" "Value" qs true nop editable-value on-qs-change])

(defn base-body-view [title body headers on-change on-visibility-toggled]
  (let [content-type (get headers "Content-Type")]
    [:> ExpansionPanel {:elevation 0 :onChange on-visibility-toggled}
      [:> ExpansionPanelSummary {:expandIcon (r/as-element [:> ExpandMoreIcon])}
        title]
      [:> ExpansionPanelDetails
        (if (nil? body)
          [:> CircularProgress]
          [styled-editor body content-type on-change])]]))

(defn body-view
  ([title body headers]
    [body-view title body headers nop])
  ([title body headers on-visibility-toggled]
    [base-body-view title body headers nop on-visibility-toggled]))

(defn editable-body-view [title body headers on-change]
  [base-body-view title body headers on-change nop])
