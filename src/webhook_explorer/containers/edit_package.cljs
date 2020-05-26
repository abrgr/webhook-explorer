(ns webhook-explorer.containers.edit-package
  (:require [clojure.string :as string]
            [reagent.core :as r]
            [goog.object :as obj]
            [webhook-explorer.styles :as styles]
            [webhook-explorer.xstate :as xs]
            [webhook-explorer.icons :as icons]
            [webhook-explorer.app-state :as app-state]
            [webhook-explorer.actions.handlers :as handlers-actions]
            [webhook-explorer.components.req-parts :as req-parts]
            [webhook-explorer.components.method-selector :as method-selector]
            [webhook-explorer.components.req-captures :as req-captures]
            [webhook-explorer.components.card-list :as card-list]
            [webhook-explorer.components.req-editor :as req-editor]
            [webhook-explorer.components.bottom-container :as bottom-container]
            [webhook-explorer.env :as env]
            ["@material-ui/core/IconButton" :default IconButton]
            ["@material-ui/icons/Delete" :default DeleteIcon]
            ["@material-ui/icons/Add" :default AddIcon]
            ["@material-ui/icons/ArrowDownward" :default DownArrowIcon]
            ["@material-ui/icons/ArrowUpward" :default UpArrowIcon]
            ["@material-ui/core/Typography" :default Typography]
            ["@material-ui/core/Paper" :default Paper]
            ["@material-ui/core/TextField" :default TextField]
            ["@material-ui/icons/Publish" :default SaveIcon]
            ["@material-ui/core/Snackbar" :default Snackbar]))

(def ^:private styled
  (styles/style-wrapper
   (fn [theme]
     {:failed-container {:display "flex"
                         :flexDirection "column"
                         :height "100%"
                         :alignItems "center"
                         :justifyContent "center"}
      :disabled {:color "rgba(0, 0, 0, 0.26)"}
      :right-controls {:display "flex"
                       :flex-direction "row"
                       :justify-content "flex-end"}
      :capture-container {:width "100%"
                          :height "100%"
                          :padding-right 25}
      :capture-container-inner {:overflow "scroll"
                                :width "100%"
                                :height "100%"
                                "&::-webkit-scrollbar" {:background "transparent"}
                                "&::-webkit-scrollbar-thumb" {:background "#ececec"}}
      :capture-item {:float "left"
                     :margin-right 50}})))

(defn- request* [{:keys [idx svc state class-name styles]
                  {:keys [req-name]
                   {header-captures :headers
                    status-capture :status
                    {body-capture-type :type
                     body-captures :captures} :body} :captures
                   {:keys [protocol method host path qs headers body]} :req} :item}]
  [:> Paper {:elevation 3
             :className class-name}
   [:div {:className (obj/get styles "right-controls")}
    [:> IconButton {:onClick (fn []
                               (xs/send
                                svc
                                {:type :remove-req
                                 :req-idx idx}))}
     [:> DeleteIcon]]]
   [:> TextField
    {:fullWidth true
     :label "Request name"
     :value req-name
     :onChange #(xs/send svc {:type :update-req-name :req-idx idx :req-name (obj/getValueByKeys % #js ["target" "value"])})}]
   [:> Paper {:elevation 3
              :className class-name}
    [:> Typography {:variant "h6"
                    :color "textSecondary"}
     "Request"]
    [:> Typography {:variant "caption"
                    :component "p"}
     "You can use a "
     [:a {:href "http://mustache.github.io/mustache.5.html"
          :target "_blank"}
      "mustache-style"]
     " {{template-var}} or {{#list-var}}{{val}}{{/list-var}} in any field."]
    [req-editor/component
     {:protocol protocol
      :method method
      :host host
      :path path
      :qs qs
      :headers headers
      :body body
      :on-update (fn [k v] (xs/send svc {:type :update-req :req-idx idx :k k :v v}))}]]
   [req-captures/component
    {:type :response
     :status-capture (:template-var status-capture)
     :header-captures (req-captures/template-var-map->simple-map header-captures)
     :body-capture-type body-capture-type
     :body-captures (req-captures/template-var-map->simple-map body-captures)
     :on-update-header-capture #(xs/send svc {:type :update-header-capture :req-idx idx :header %1 :template-var %2})
     :on-remove-header-capture #(xs/send svc {:type :remove-header-capture :req-idx idx :header %})
     :on-remove-all-body-captures #(xs/send svc {:type :remove-all-body-captures :req-idx idx})
     :on-update-body-capture-type #(xs/send svc {:type :update-body-capture-type :req-idx idx :body-capture-type %})
     :on-update-body-capture #(xs/send svc {:type :update-body-capture :req-idx idx :body-capture-key %1 :template-var %2})
     :on-remove-body-capture #(xs/send svc {:type :remove-body-capture :req-idx idx :body-capture-key %})
     :on-should-capture-status #(xs/send svc {:type :update-status-capture :req-idx idx :template-var (if % "" nil)})
     :on-update-status-capture #(xs/send svc {:type :update-status-capture :req-idx idx :template-var %})}]])

(defn request [props]
  [styled props request*])

(defn- state->items [state]
  (get-in state [:context :package :reqs]))

(defn input-template-vars []
  (let [v (r/atom "")]
    (fn [{:keys [svc vars]}]
      [:<>
       [:> Typography {:variant "h6"
                       :paragraph true
                       :style #js {:marginTop 20}}
        "Input template variables"]
       [:> Typography {:variant "caption"
                       :paragraph true}
        "Input template variables must be provided when a request package is invoked, either by web form or as columns in a spreadsheet when invoked in bulk."]
       [:ul
        (for [template-var vars]
          ^{:key template-var}
          [:li
           template-var
           [:> IconButton {:on-click #(xs/send svc {:type :remove-input-template-var :template-var template-var})}
            [:> DeleteIcon]]])]
       [:div
        [:> TextField {:label "New input variable"
                       :value @v
                       :on-change #(reset! v (obj/getValueByKeys % #js ["target" "value"]))}]
        [:> IconButton {:onClick #(do (xs/send svc {:type :add-input-template-var :template-var @v})
                                      (reset! v ""))}
         [:> AddIcon]]]])))

(defn- preamble* [{:keys [styles state svc]}]
  [:<>
   [:> Snackbar
    (let [n (get-in state [:context :notification])]
      {:open (boolean n)
       :message n})]
   [:> Typography {:component "p"
                   :paragraph true}
    "A request package is a set of requests that can have arbitrary dependencies
     on one another by capturing aspects of the result of one request in a
     template variable that can then be referenced in other requests.
     The order of requests here doesn't matter. Requests will be executed
     in the order implied by the graph of template variable references."]
   [:> Typography {:component "p"
                   :paragraph true}
    "When you capture a template variable, 'tempVar', in a request named, 'myReq',
       it may be referenced as:"]
   [:ul
    [:li "{{every.myReq.tempVar}} for the single tempVar captured for each instance of myReq, repeating any request with such a reference once for every instance of myReq"]
    [:li "{{#all.myReq.tempVar}}{{.}}{{/all.myReq.tempVar}} for the array of tempVars captured from all instances of myReq, running any request with such a reference only once after all instances of myReq complete"]]
   [:> Typography {:component "p"
                   :paragraph true}
    "Additionally, you may reference {{input_variable}} to reference input variables 
     that you expect to be passed to this request package when it's invoked."]
   [:> TextField
    {:fullWidth true
     :label "Package name"
     :value (get-in state [:context :package :name])
     :onChange #(xs/send svc {:type :update-package-name :package-name (obj/getValueByKeys % #js ["target" "value"])})}]
   [input-template-vars {:svc svc
                         :vars (get-in state [:context :package :input-template-vars])}]
   [bottom-container/component
    {:on-btn-click #(xs/send svc :save)
     :btn-loading (xs/matches? state :ready.saving)
     :btn-icon-component (r/adapt-react-class SaveIcon)
     :btn-title "Save"}
    [:div {:className (obj/get styles "capture-container")}
     [:div {:className (obj/get styles "capture-container-inner")}
      [:div {:class-name (obj/get styles "capture-item")
             :style #js {:width 100
                         :marginRight 50}}
       [:> Typography {:variant "h6"
                       :color "textSecondary"}
        "Available template variables"]]
      (let [input-template-vars (get-in state [:context :package :input-template-vars])]
        (when (not-empty input-template-vars)
          [:div {:class-name (obj/get styles "capture-item")}
           [:> Typography
            {:variant "h5"}
            "Input template variables"]
           (for [template-var input-template-vars]
             ^{:key template-var}
             [:> Typography
              {:variant "subtitle1"}
              template-var])]))
      (for [{:keys [req-name captures]} (get-in state [:context :package :reqs])
            :let [caps (concat (filter some? [(get captures :status)])
                               (-> captures :headers vals)
                               (vals (get-in captures [:body :captures])))]
            :when (not-empty caps)]
        ^{:key req-name}
        [:div {:class-name (obj/get styles "capture-item")}
         [:> Typography
          {:variant "h5"}
          req-name]
         (for [{c :template-var} caps]
           ^{:key c}
           [:> Typography
            {:variant "subtitle1"}
            c])])]]]])

(defn- preamble [props]
  [styled props preamble*])

(defn- postamble* [{:keys [styles]}]
  [bottom-container/spacer-component])

(defn- postamble [props]
  [styled props postamble*])

(defn- failed-component* [{:keys [styles]}]
  [:div {:className (obj/get styles "failed-container")}
   [:> icons/RequestPackageIcon {:style #js {:fontSize 100}
                                 :color "disabled"}]
   [:> Typography {:variant "h4"
                   :class-name (obj/get styles "disabled")}
    "Failed to load this request package"]])

(defn- failed-component [props]
  [styled props failed-component*])

(defn- -component* [{:keys [svc state]}]
  [card-list/component
   {:svc svc
    :state state
    :preamble-component preamble
    :postamble-component postamble
    :failed-component failed-component
    :item-renderer request
    :state->items state->items
    :ready-state :ready
    :failed-state :failed
    :add-item-title "Add a request."
    :on-add-item #(xs/send svc :add-req)}])

(defn component []
  [xs/with-svc {:svc app-state/edit-package}
   (fn [state]
     [-component* {:svc app-state/edit-package
                   :state state}])])
