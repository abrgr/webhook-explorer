(ns webhook-explorer.containers.edit-handler
  (:require [clojure.string :as string]
            [reagent.core :as r]
            [goog.object :as obj]
            [webhook-explorer.styles :as styles]
            [webhook-explorer.xstate :as xs]
            [webhook-explorer.app-state :as app-state]
            [webhook-explorer.actions.handlers :as handlers-actions]
            [webhook-explorer.components.req-parts :as req-parts]
            [webhook-explorer.components.method-selector :as method-selector]
            [webhook-explorer.env :as env]
            ["@material-ui/core/CircularProgress" :default CircularProgress]
            ["@material-ui/core/ListSubheader" :default ListSubheader]
            ["@material-ui/core/Chip" :default Chip]
            ["@material-ui/core/IconButton" :default IconButton]
            ["@material-ui/icons/ArrowDownward" :default DownArrowIcon]
            ["@material-ui/icons/ArrowUpward" :default UpArrowIcon]
            ["@material-ui/core/Select" :default Select]
            ["@material-ui/core/MenuItem" :default MenuItem]
            ["@material-ui/core/InputLabel" :default InputLabel]
            ["@material-ui/core/Divider" :default Divider]
            ["@material-ui/core/Typography" :default Typography]
            ["@material-ui/core/Tooltip" :default Tooltip]
            ["@material-ui/core/Fab" :default Fab]
            ["@material-ui/core/Paper" :default Paper]
            ["@material-ui/core/Radio" :default Radio]
            ["@material-ui/core/RadioGroup" :default RadioGroup]
            ["@material-ui/core/FormControl" :default FormControl]
            ["@material-ui/core/FormControlLabel" :default FormControlLabel]
            ["@material-ui/core/FormLabel" :default FormLabel]
            ["@material-ui/core/TextField" :default TextField]
            ["@material-ui/icons/Publish" :default SaveIcon]
            ["@material-ui/icons/Add" :default AddIcon]))

(def ^:private bottom-container-height 150)

(def ^:private styled
  (styles/style-wrapper
   (fn [theme]
     {:flex-container {:display "flex"
                       :align-items "center"}
      :extended-icon {:marginRight (.spacing theme 1)}
      :container {:width "80%"
                  :height "100%"
                  :minWidth "480px"
                  :maxWidth "768px"
                  :margin "25px auto"}
      :path-container {:display "flex"
                       :alignItems "flex-start"}
      :full-flex {:flex 1
                  :marginLeft 20}
      :divider {:margin-top 16
                :margin-bottom 16}
      :2-col-container {:display "flex"
                        "& .MuiExpansionPanelSummary-root" {:padding 0}}
      :right-controls {:display "flex"
                       :flex-direction "row"
                       :justify-content "flex-end"}
      :left-container {:width 100}
      :caption-container {:position "relative"}
      :caption {:position "absolute"
                :bottom -48}
      :capture-container {:marginTop 20
                          :padding 20
                          "& .MuiExpansionPanelSummary-root" {:padding 0}}
      :template-var-container {:flex 1}
      :template-caption {:margin-top 20
                         :margin-bottom 10}
      :subheader {:background-color "#fff"}
      :chip {:margin 10}
      :publish-container {:margin "auto"}
      :bottom-container {:position "fixed"
                         :display "flex"
                         :left 0
                         :right 0
                         :bottom 0
                         :height bottom-container-height
                         :border-top "2px solid #eee"
                         :z-index 100
                         :padding 20}
      :bottom-container-spacer {:height (+ bottom-container-height 50)}
      :matcher-container {:marginTop 48
                          :padding 20}
      :add-matcher-container {:display "flex"
                              :flexDirection "column"
                              :alignItems "center"}})))

(def ^:private match-types
  (array-map
   :exact {:label "Exact match"}
   :prefix {:label "Prefix match"}))

(def ^:private handler-types
  (array-map
   :mock {:label "Mock"}
   :proxy {:label "Proxy"}))

(def ^:private body-capture-types
  (array-map
   :json {:label "JSON"}
   :form-data {:label "Form Data"}))

(def ^:private status-codes
  (array-map
   100 "Continue"
   101 "Switching Protocols"
   102 "Processing"
   103 "Early Hints"
   200 "OK"
   201 "Created"
   202 "Accepted"
   203 "Non-Authoritative Information"
   204 "No Content"
   205 "Reset Content"
   206 "Partial Content"
   207 "Multi-Status"
   208 "Already Reported"
   226 "IM Used"
   300 "Multiple Choices"
   301 "Moved Permanently"
   302 "Found"
   303 "See Other"
   304 "Not Modified"
   305 "Use Proxy"
   307 "Temporary Redirect"
   308 "Permanent Redirect"
   400 "Bad Request"
   401 "Unauthorized"
   402 "Payment Required"
   403 "Forbidden"
   404 "Not Found"
   405 "Method Not Allowed"
   406 "Not Acceptable"
   407 "Proxy Authentication Required"
   408 "Request Timeout"
   409 "Conflict"
   410 "Gone"
   411 "Length Required"
   412 "Precondition Failed"
   413 "Payload Too Large"
   414 "URI Too Long"
   415 "Unsupported Media Type"
   416 "Range Not Satisfiable"
   417 "Expectation Failed"
   421 "Misdirected Request"
   422 "Unprocessable Entity"
   423 "Locked"
   424 "Failed Dependency"
   425 "Too Early"
   426 "Upgrade Required"
   428 "Precondition Required"
   429 "Too Many Requests"
   431 "Request Header Fields Too Large"
   451 "Unavailable For Legal Reasons"
   500 "Internal Server Error"
   501 "Not Implemented"
   502 "Bad Gateway"
   503 "Service Unavailable"
   504 "Gateway Timeout"
   505 "HTTP Version Not Supported"
   506 "Variant Also Negotiates"
   507 "Insufficient Storage"
   508 "Loop Detected"
   510 "Not Extended"
   511 "Network Authentication Required"))

(defn- get-target-value [evt]
  (obj/getValueByKeys evt #js ["target" "value"]))

(defmulti handler-component (fn [{{:keys [type]} :handler}] type))

(defmethod handler-component :default [_]
  [:div])

(defmethod handler-component :mock [{{{{:keys [headers status]} :res :as res} :mock} :handler
                                     :keys [styles idx on-update]}]
  [:<>
   [:> Typography {:variant "caption"
                   :component "p"
                   :className (obj/get styles "template-caption")}
    "You can write ${template-var} in any response header value or anywhere in the body"]
   [:> FormControl {:margin "normal"
                    :fullWidth true}
    [:> InputLabel "Response status"]
    [:> Select {:value (or status "")
                :fullWidth true
                :MenuProps #js {:PaperProps #js {:style #js {:maxHeight 250}}}
                :onChange #(on-update assoc-in [:matchers idx :handler :mock :res :status] (js/parseInt (get-target-value %) 10))}
     (mapcat
      (fn [[status label]]
        [(when (zero? (mod status 100))
           ^{:key (str "header-" status)}
           [:> ListSubheader {:classes #js {:root (obj/get styles "subheader")}}
            (str (quot status 100) "xx")])
         ^{:key status}
         [(r/adapt-react-class MenuItem) {:value (str status)} (str status " - " label)]])
      status-codes)]]
   [req-parts/editable-headers-view
    "Response headers"
    (or headers {})
    (fn [k v]
      (if (nil? v)
        (on-update update-in [:matchers idx :handler :mock :res :headers] dissoc (name k))
        (on-update assoc-in [:matchers idx :handler :mock :res :headers (name k)] v)))]
   [req-parts/base-body-view
    {:title "Body"
     :bodies (req-parts/make-bodies {:raw {:label "Raw" :body ""}})
     :headers headers
     :on-change (partial on-update assoc-in [:matchers idx :handler :mock :res :body])}]])

(defmethod handler-component :proxy [{{:keys [proxy]} :handler :keys [idx on-update]}]
  (let [{:keys [remote-url]} proxy]
    [:> TextField
     {:label "Remote URL"
      :fullWidth true
      :helperText "URL to proxy matching requests to. Can include {template-variables}."
      :value (or remote-url "")
      :onChange #(on-update assoc-in [:matchers idx :handler :proxy :remote-url] (get-target-value %))}]))

(defn- domain-selector [{:keys [value on-change class-name]}]
  [:> FormControl {:fullWidth true
                   :margin "normal"
                   :className class-name}
   [:> InputLabel "Domain"]
   [:> Select {:value (or value "")
               :onChange #(on-change (obj/getValueByKeys % #js ["target" "value"]))}
    (for [domain env/handler-domains]
      ^{:key domain}
      [(r/adapt-react-class MenuItem) {:value domain} domain])]])

(defn- path-component [{:keys [styles match-type path method domain on-update]}]
  [:div {:className (obj/get styles "path-container")}
   [:> FormControl {:component "fieldset"
                    :margin "normal"}
    [:> FormLabel {:component "legend"}
     "Path match type"]
    [:> RadioGroup {:aria-label "Path match type"
                    :value (if match-type (name match-type) "")
                    :onChange #(on-update assoc :match-type (keyword (get-target-value %)))}
     (for [[match-type {:keys [label]}] match-types]
       ^{:key match-type}
       [(r/adapt-react-class FormControlLabel)
        {:label label
         :value (name match-type)
         :control (r/as-element [:> Radio])}])]]
   [:div
    [:div {:style #js {:display "flex"}}
     [domain-selector
      {:class-name (obj/get styles "full-flex")
       :value domain
       :on-change #(on-update assoc :domain %)}]
     [method-selector/component
      {:class-name (obj/get styles "full-flex")
       :value method
       :on-change #(on-update assoc :method %)}]]
    [:> FormControl {:margin "normal"
                     :className (obj/get styles "full-flex")}
     [:> TextField {:label "Path"
                    :helperText (r/as-element
                                 [:<>
                                  [:span "Exact match against '/the/{path}/here' matches '/the/path/here', '/the/other-path/here', etc."]
                                  [:br]
                                  [:span "Prefix match against '/the/{path}/here' matches '/the/path/here', '/the/other-path/here/and/here', etc."]
                                  [:br]
                                  [:span "Exact and prefix matches are ranked by longest matching concrete prefix (without variables) first."]
                                  [:br]
                                  [:span "Exact matches are ranked before prefix matches."]])
                    :value path
                    :onChange #(on-update assoc :path (get-target-value %))}]]]])

(defn- move-item [idx dir v]
  (let [op (case dir
             :up dec
             :down inc)]
    (assoc v
           idx (get v (op idx))
           (op idx) (get v idx))))

(defn- path->template-vars [path]
  (->> (re-seq #"[{]([^}]+)[}]" path)
       (map second)))

(defn- make-template-var-picker [template-vars]
  (fn template-var-picker [{:keys [value on-change]}]
    [:> Select {:value (or value "")
                :fullWidth true
                :onChange #(on-change (get-target-value %))}
     (for [tv template-vars]
       ^{:key tv}
       [(r/adapt-react-class MenuItem) {:value tv} tv])]))

(defn- matcher [{:keys [styles idx total-matcher-count template-vars handler matches on-update]}]
  (let [on-update-input (fn [k evt]
                          (on-update assoc-in [:matchers idx k] (get-target-value evt)))
        handler-type (:type handler)]
    [:> Paper {:elevation 3
               :className (obj/get styles "matcher-container")}
     [:div {:className (obj/get styles "right-controls")}
      [:> IconButton {:disabled (= idx 0)
                      :onClick #(on-update
                                 update
                                 :matchers
                                 (partial move-item idx :up))}
       [:> UpArrowIcon]]
      [:> IconButton {:disabled (= idx (dec total-matcher-count))
                      :onClick #(on-update
                                 update
                                 :matchers
                                 (partial move-item idx :down))}
       [:> DownArrowIcon]]]
     [:div {:className (obj/get styles "2-col-container")}
      [:div {:className (obj/get styles "left-container")}
       [:> Typography {:variant "h5"
                       :color "textSecondary"}
        "When:"]]
      [:div {:className (obj/get styles "full-flex")}
       (if (empty? template-vars)
         [:<>
          [:> Typography "No template variables available."]
          [:> Typography "Capture template variables or add path variables, above, to match against."]]
         [:> FormControl {:fullWidth true
                          :margin "normal"}
          [:> InputLabel "Request body matcher"]
          [req-parts/base-kv-view
           {:title (str "Template variable matches (" (count matches) ")")
            :k-title "Template variable to check"
            :v-title "Matched value"
            :m matches
            :editable true
            :value-component req-parts/editable-value
            :key-editor-component (make-template-var-picker template-vars)
            :on-change (fn [tv v]
                         (if (nil? v)
                           (on-update update-in [:matchers idx :matches] dissoc (name tv))
                           (on-update assoc-in [:matchers idx :matches (name tv)] v)))}]])]]
     [:> Divider {:className (obj/get styles "divider")}]
     [:div {:className (obj/get styles "2-col-container")}
      [:div {:className (obj/get styles "left-container")}
       [:> Typography {:variant "h5"
                       :color "textSecondary"}
        "Then:"]]
      [:div {:className (obj/get styles "full-flex")}
       [:> FormControl {:fullWidth true
                        :margin "normal"}
        [:> InputLabel "Handle with"]
        [:> Select {:value (if handler-type (name handler-type) "")
                    :onChange #(on-update assoc-in [:matchers idx :handler] {:type (keyword (get-target-value %))})}
         (for [[rt {:keys [label]}] handler-types]
           ^{:key rt}
           [(r/adapt-react-class MenuItem) {:value (name rt)} label])]]
       [handler-component {:handler handler
                           :idx idx
                           :on-update on-update
                           :styles styles}]]]]))

(def ^:private new-matcher-template
  {:matches {}
   :handler nil})

(defn- template-var-map->simple-map [m]
  (->> m
       (map (fn [[k {:keys [template-var]}]] [k template-var]))
       (into {})))

(def ^:private capture-key-title
  {:json "JSON path to capture"
   :form-data "Form field to capture"})

(defn- captures [{:keys [styles path header-captures body-capture-type body-captures on-update]}]
  [:> Paper {:elevation 3
             :className (obj/get styles "capture-container")}
   [:> Typography {:variant "h6"
                   :color "textSecondary"}
    "Capture Template Variables"]
   [:div {:className (obj/get styles "full-flex")}
    [req-parts/editable-headers-view
     (str "Request header captures (" (count header-captures) ")")
     (template-var-map->simple-map header-captures)
     (fn [k v]
       (if (nil? v)
         (on-update update-in [:captures :headers] dissoc (name k))
         (on-update assoc-in [:captures :headers (name k) :template-var] v)))]
    [:> FormControl {:fullWidth true
                     :margin "normal"}
     [:> InputLabel "Request body captures"]
     [:> Select {:value (if body-capture-type (name body-capture-type) " ")
                 :onChange #(let [v (get-target-value %)]
                              (if (= v " ")
                                (on-update update :captures dissoc :body)
                                (on-update assoc-in [:captures :body] {:type (keyword v) :captures {}})))}
      [:> MenuItem {:value " "} "None"]
      (for [[mt {:keys [label]}] body-capture-types]
        ^{:key mt}
        [(r/adapt-react-class MenuItem) {:value (name mt)} label])]]
    (when (some? body-captures)
      [req-parts/base-kv-view
       {:title "Body captures"
        :k-title (capture-key-title body-capture-type)
        :v-title "Template variable"
        :m (template-var-map->simple-map body-captures)
        :editable true
        :value-component req-parts/editable-value
        :default-expanded true
        :on-change (fn [jp v]
                     (if (nil? v)
                       (on-update update-in [:captures :body :captures] dissoc (name jp))
                       (on-update assoc-in [:captures :body :captures (name jp) :template-var] v)))}])]])

(defn- get-all-template-vars [path header-captures body-captures]
  (->> (concat (vals header-captures) (vals body-captures))
       (map :template-var)
       (concat (path->template-vars path))
       (into [])))

(defn- template-var-container [{:keys [styles template-vars]}]
  [:div {:className (obj/get styles "template-var-container")}
   [:> Typography {:variant "h6"
                   :color "textSecondary"}
    "Captured template variables"]
   (if (empty? template-vars)
     [:> Typography
      "No captured variables yet"]
     (for [tv template-vars]
       ^{:key tv}
       [(r/adapt-react-class Chip) {:label tv
                                    :className (obj/get styles "chip")
                                    :variant "outlined"}]))])

(defn- main-component [{:keys [styles state on-update send]}]
  (let [{:keys [match-type path method domain matchers]
         {header-captures :headers
          {body-capture-type :type
           body-captures :captures} :body} :captures} (obj/getValueByKeys state #js ["context" "handler"])
        template-vars (get-all-template-vars path header-captures body-captures)]
    [:div {:className (obj/get styles "container")}
     [:> Paper {:className (obj/get styles "bottom-container")}
      [template-var-container {:styles styles
                               :template-vars template-vars}]
      [:div {:className (obj/get styles "publish-container")}
       [:> Fab {:variant "extended"
                :label "Save"
                :color "secondary"
                :onClick (r/partial send :publish)}
        [:> SaveIcon {:className (obj/get styles "extended-icon")}]
        "Publish changes"]]]
     [path-component {:styles styles
                      :match-type match-type
                      :path path
                      :method method
                      :domain domain
                      :on-update on-update}]
     [captures {:styles styles
                :path path
                :header-captures header-captures
                :body-capture-type body-capture-type
                :body-captures body-captures
                :on-update on-update}]
     [:div {:className (obj/get styles "caption-container")}
      [:> Typography {:variant "caption"
                      :className (obj/get styles "caption")}
       "Matchers are processed in order, top to bottom. First match wins."]]
     (map-indexed
      (fn [idx {:keys [path matches handler]}]
        ^{:key idx}
        [matcher {:styles styles
                  :idx idx
                  :total-matcher-count (count matchers)
                  :template-vars template-vars
                  :handler handler
                  :matches matches
                  :on-update on-update}])
      matchers)
     [:> Paper {:elevation 3
                :className (str
                            (obj/get styles "add-matcher-container")
                            " "
                            (obj/get styles "matcher-container"))}
      [:> Fab {:color "primary"
               :onClick #(on-update update :matchers conj new-matcher-template)}
       [:> AddIcon]]
      [:> Typography {:color "textSecondary"}
       "Add a matcher."]]
     [:div {:className (obj/get styles "bottom-container-spacer")}]]))

(defn- -component* [{:keys [styles svc state]}]
  (let [on-update (fn [& updater]
                    (xs/send svc {:type :update-handler
                                  :updater updater}))]
    (xs/case state
      :failed [:div "Failed"]
      :ready [main-component {:styles styles
                              :state state
                              :on-update on-update
                              :send (r/partial xs/send svc)}]
      [:> CircularProgress])))

(defn -component [{:keys [styles]}]
  [xs/with-svc {:svc app-state/handler}
   (fn [state]
     [-component* {:svc app-state/handler
                   :state state
                   :styles styles}])])

(defn component []
  [styled {} -component])
