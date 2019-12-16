(ns webhook-explorer.components.req-parts
  (:require [reagent.core :as r]
            [webhook-explorer.init :as init]
            [webhook-explorer.styles :as styles]
            [goog.object :as obj]
            ["@material-ui/core/IconButton" :default IconButton]
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

(defn base-headers-view
  ([title headers show-delete on-visibility-toggled header-value-component]
    [base-headers-view title headers show-delete on-visibility-toggled header-value-component {}])
  ([title headers show-delete on-visibility-toggled header-value-component on-header-change]
    [:> ExpansionPanel {:elevation 0
                        :TransitionProps #js {:onEntered on-visibility-toggled
                                              :onExit on-visibility-toggled
                                              :unmountOnExit true}}
      [:> ExpansionPanelSummary {:expandIcon (r/as-element [:> ExpandMoreIcon])}
        title]
      [:> ExpansionPanelDetails
        (if (nil? headers)
          [:> CircularProgress]
          [:> Table {:aria-label "headers"}
            [:> TableHead
              [:> TableRow
                (when show-delete
                  [:> TableCell ""])
                [:> TableCell "Header"]
                [:> TableCell "Value"]]]
            [:> TableBody
              (for [[header value] headers]
                ^{:key header}
                [:> TableRow
                  (when show-delete
                    [:> TableCell
                      [:> IconButton {:aria-label "delete"
                                      :onClick #(on-header-change header nil)}
                        [:> DeleteIcon {:fontSize "small"}]]])
                  [:> TableCell header]
                  [:> TableCell [header-value-component {:value value :header header :on-header-change on-header-change}]]])]])]]))

(defn- base-header-value [{:keys [value]}]
  value)

(defn- nop [] nil)

(defn headers-view
  ([title headers]
    [headers-view title headers nop])
  ([title headers on-visibility-toggled]
    [base-headers-view title headers false on-visibility-toggled base-header-value]))

(defn- editable-header-value [{:keys [header value on-header-change]}]
  ^{:key header}
  [:> TextField {:value value
                 :onChange #(on-header-change header (obj/getValueByKeys % #js ["target" "value"]))}])

(defn editable-headers-view [title headers on-header-change]
  [base-headers-view title headers true nop editable-header-value on-header-change])

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
