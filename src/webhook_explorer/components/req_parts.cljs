(ns webhook-explorer.components.req-parts
  (:require [reagent.core :as r]
            [webhook-explorer.init :as init]
            [webhook-explorer.styles :as styles]
            [goog.object :as obj]
            ["@material-ui/core/Tabs" :default Tabs]
            ["@material-ui/core/Tab" :default Tab]
            ["@material-ui/core/IconButton" :default IconButton]
            ["@material-ui/icons/Add" :default AddIcon]
            ["@material-ui/icons/Delete" :default DeleteIcon]
            ["@material-ui/icons/CloudDownload" :default DownloadIcon]
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
              :marginTop 16
              "& .CodeMirror-scroll" {:maxHeight 500}
              "& > .CodeMirror" {:height "auto"
                                 :width "100%"
                                 :border "1px solid #eee"}}})))

(defn- editor [{:keys [value content-type onChange styles] :as p}]
  (let [mode (when-let [m (.findModeByMIME CM (or content-type "text/plain"))]
               (obj/get m "mode"))]
    (r/as-element
      [:> CodeMirror {:className (obj/get styles "code")
                      :value value
                      :onChange onChange
                      :options #js {:lineWrapping true
                                    :lineNumbers true
                                    :mode mode}}])))

(defn- styled-editor [value content-type on-change]
  [styled {:value value :content-type content-type :onChange on-change} editor])

(defn- nop [] nil)

(defn- base-value [{:keys [value]}]
  value)

(defn- default-key-editor-component [{:keys [value on-change]}]
  [:> TextField {:value value
                 :onChange #(on-change (obj/getValueByKeys % #js ["target" "value"]))}])

(defn base-kv-view []
  (let [new-kv (r/atom {:new-k "" :new-v ""})]
    (fn [{:keys [title k-title v-title m editable on-visibility-toggled key-editor-component value-component default-expanded on-change]
          :or {on-change nop
               value-component base-value
               key-editor-component default-key-editor-component
               on-visibility-toggled nop}}]
      (let [{:keys [new-k new-v]} @new-kv]
        [:> ExpansionPanel {:elevation 0
                            :defaultExpanded default-expanded
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
                    [:> TableCell k-title]
                    [:> TableCell v-title]
                    (when editable
                      [:> TableCell ""])]]
                [:> TableBody
                  (for [[k v] m]
                    ^{:key k}
                    [:> TableRow
                      [:> TableCell k]
                      [:> TableCell [value-component {:value v :key k :on-change on-change}]]
                      (when editable
                        [:> TableCell
                          [:> IconButton {:aria-label "delete"
                                          :onClick #(on-change k nil)}
                            [:> DeleteIcon {:fontSize "small"}]]])])]
                (when editable
                  [:> TableFooter
                    [:> TableRow
                      [:> TableCell
                        [key-editor-component {:value new-k
                                               :on-change #(swap! new-kv assoc :new-k %)}]]
                      [:> TableCell
                        [:> TextField {:value new-v
                                       :onChange #(swap! new-kv assoc :new-v (obj/getValueByKeys % #js ["target" "value"]))}]]
                      [:> TableCell
                        [:> IconButton {:aria-label "add"
                                        :onClick #(do (on-change (keyword new-k) new-v)
                                                      (reset! new-kv {:new-k "" :new-v ""}))}
                          [:> AddIcon {:color "primary"
                                       :fontSize "small"}]]]]])])]]))))

(defn headers-view
  ([title headers]
    [headers-view title headers nop])
  ([title headers on-visibility-toggled]
    [base-kv-view {:title title
                   :k-title "Header"
                   :v-title "Value"
                   :m headers
                   :on-visibility-toggled on-visibility-toggled}]))

(defn cookies-view
  ([title cookies]
    [cookies-view title cookies nop])
  ([title cookies on-visibility-toggled]
    [base-kv-view {:title title
                   :k-title "Cookie"
                   :v-title "Value"
                   :m cookies
                   :on-visibility-toggled on-visibility-toggled}]))

(defn- editable-value [{:keys [key value on-change]}]
  ^{:key key}
  [:> TextField {:value value
                 :onChange #(on-change key (obj/getValueByKeys % #js ["target" "value"]))}])

(defn editable-headers-view [title headers on-header-change]
  [base-kv-view {:title title
                 :k-title "Header"
                 :v-title "Value"
                 :m headers
                 :editable true
                 :value-component editable-value
                 :on-change on-header-change}])

(defn qs-view [title qs on-visibility-toggled]
  [base-kv-view {:title title
                 :k-title "Key"
                 :v-title "Value"
                 :m qs
                 :on-visibility-toggled on-visibility-toggled}])

(defn editable-qs-view [title qs on-qs-change]
  [base-kv-view {:title title
                 :k-title "Key"
                 :v-title "Value"
                 :m qs
                 :editable true
                 :value-component editable-value
                 :on-change on-qs-change}])

(defmulti inner-body-view (fn [{:keys [type]}] type))

(defmethod inner-body-view :raw [{:keys [body content-type on-change]}]
  [styled-editor body content-type on-change])

(defmethod inner-body-view :fields [{:keys [body on-change]}]
  (let [fields (->> body
                    (map (fn [[k v]] [(name k) v]))
                    (into {}))]
    [base-kv-view {:title "Fields"
                   :k-title "Name"
                   :v-title "Value"
                   :m fields
                   :default-expanded true}]))

(defmethod inner-body-view :files [{:keys [body]}]
  [:> Table {:aria-label "Files"}
    [:> TableHead
      [:> TableRow
        [:> TableCell "Filename"]
        [:> TableCell "Mime Type"]
        [:> TableCell "Encoding"]
        [:> TableCell ""]]]
    [:> TableBody
      (for [{:keys [filename mimetype originalEncoding data]} body]
        ^{:key filename}
        [:> TableRow
          [:> TableCell filename]
          [:> TableCell mimetype]
          [:> TableCell originalEncoding]
          [:> TableCell
            [:> IconButton {:aria-label "download"
                            :download filename
                            :href (str "data:" mimetype ";base64," data)}
              [:> DownloadIcon {:fontSize "small"}]]]])]])

(defn- body-tabs [{:keys [bodies content-type on-visibility-toggled on-change]}]
  (let [tab (r/atom (-> bodies first))]
    (fn [{:keys [bodies content-type on-change]}]
      (let [{:keys [type label] :as t} @tab]
        [:div {:style #js {:width "100%"}}
          [:> Tabs {:value type
                    :label label
                    :indicatorColor "primary"
                    :textColor "primary"
                    :variant "fullWidth"
                    :onChange (fn [_ v]
                                (->> bodies
                                     (filter #(= (:type %) (keyword v)))
                                     first
                                     (reset! tab))
                                (on-visibility-toggled))}
            (for [{:keys [type label] :as body} bodies]
              ^{:key label}
              [(r/adapt-react-class Tab) {:value type :label label}])]
          [inner-body-view (merge t {:content-type content-type :on-change on-change})]]))))

(defn base-body-view [title bodies headers on-change on-visibility-toggled]
  (let [content-type (get headers "Content-Type")]
    [:> ExpansionPanel {:elevation 0 :onChange on-visibility-toggled}
      [:> ExpansionPanelSummary {:expandIcon (r/as-element [:> ExpandMoreIcon])}
        title]
      [:> ExpansionPanelDetails
        (cond
          (and (nil? bodies) (nil? headers)) [:> CircularProgress]
          (= (count bodies) 1) (let [{:keys [type body]} (-> bodies first)]
                                 [inner-body-view {:type type
                                                   :body body
                                                   :content-type content-type
                                                   :on-change on-change}])
          :else [body-tabs {:bodies bodies
                            :content-type content-type
                            :on-visibility-toggled on-visibility-toggled
                            :on-change on-change}])]]))

(defn make-bodies [bodies-by-type]
  (->> bodies-by-type
       (reduce
         (fn [bodies [type {:keys [label body]}]]
           (if body
             (conj (or bodies []) {:type type :label label :body body})
             bodies))
         nil)))

(defn body-view
  ([title bodies headers]
    [body-view title bodies headers nop])
  ([title bodies headers on-visibility-toggled]
    [base-body-view title bodies headers nop on-visibility-toggled]))

(defn editable-body-view [title bodies headers on-change]
  [base-body-view title bodies headers on-change nop])
