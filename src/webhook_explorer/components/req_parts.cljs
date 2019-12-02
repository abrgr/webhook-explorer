(ns webhook-explorer.components.req-parts
  (:require [reagent.core :as r]
            [webhook-explorer.init :as init]
            [webhook-explorer.styles :as styles]
            [goog.object :as obj]
            ["@material-ui/core/ExpansionPanel" :default ExpansionPanel]
            ["@material-ui/core/ExpansionPanelSummary" :default ExpansionPanelSummary]
            ["@material-ui/icons/ExpandMore" :default ExpandMoreIcon]
            ["@material-ui/core/ExpansionPanelDetails" :default ExpansionPanelDetails]
            ["@material-ui/core/CircularProgress" :default CircularProgress]
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

(defn- editor [{:keys [value content-type styles]}]
  (let [mode (when-let [m (.findModeByMIME CM (or content-type "text/plain"))]
               (obj/get m "mode"))]
    (r/as-element
      [:> CodeMirror {:className (obj/get styles "code")
                      :value value
                      :options #js {:viewportMargin ##Inf
                                    :mode mode}}])))

(defn- styled-editor [value content-type]
  [styled {:value value :content-type content-type} editor])

(defn headers-view
  ([title headers]
    [headers-view headers (constantly nil)])
  ([title headers on-visibility-toggled]
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
                [:> TableCell "Header"]
                [:> TableCell "Value"]]]
            [:> TableBody
              (for [[header value] headers]
                ^{:key header}
                [:> TableRow
                  [:> TableCell header]
                  [:> TableCell value]])]])]]))

(defn body-view
  ([title body content-type]
    [body-view title body content-type (constantly nil)])
  ([title body content-type on-visibility-toggled]
    [:> ExpansionPanel {:elevation 0 :onChange on-visibility-toggled}
      [:> ExpansionPanelSummary {:expandIcon (r/as-element [:> ExpandMoreIcon])}
        title]
      [:> ExpansionPanelDetails
        (if (nil? body)
          [:> CircularProgress]
          [styled-editor body content-type])]]))

