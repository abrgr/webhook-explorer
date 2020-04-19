(ns webhook-explorer.containers.edit-package
  (:require [clojure.string :as string]
            [reagent.core :as r]
            [goog.object :as obj]
            [webhook-explorer.styles :as styles]
            [webhook-explorer.xstate :as xs]
            [webhook-explorer.app-state :as app-state]
            [webhook-explorer.actions.handlers :as handlers-actions]
            [webhook-explorer.components.req-parts :as req-parts]
            [webhook-explorer.components.method-selector :as method-selector]
            [webhook-explorer.components.req-captures :as req-captures]
            [webhook-explorer.components.card-list :as card-list]
            [webhook-explorer.components.req-editor :as req-editor]
            [webhook-explorer.env :as env]
            ["@material-ui/core/CircularProgress" :default CircularProgress]
            ["@material-ui/core/ListSubheader" :default ListSubheader]
            ["@material-ui/core/Chip" :default Chip]
            ["@material-ui/core/IconButton" :default IconButton]
            ["@material-ui/icons/Delete" :default DeleteIcon]
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
                       :align-items "center"
                       :justify-content "center"}
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

(defn- request* [{:keys [idx svc state class-name styles]
                 {:keys [req-name]
                  {header-captures :headers
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
    {:header-captures (req-captures/template-var-map->simple-map header-captures)
     :body-capture-type body-capture-type
     :body-captures (req-captures/template-var-map->simple-map body-captures)
     :on-update-header-capture #(xs/send svc {:type :update-header-capture :req-idx idx :header %1 :template-var %2})
     :on-remove-header-capture #(xs/send svc {:type :remove-header-capture :req-idx idx :header %})
     :on-remove-all-body-captures #(xs/send svc {:type :remove-all-body-captures :req-idx idx})
     :on-update-body-capture-type #(xs/send svc {:type :update-body-capture-type :req-idx idx :body-capture-type %})
     :on-update-body-capture #(xs/send svc {:type :update-body-capture :req-idx idx :body-capture-key %1 :template-var %2})
     :on-remove-body-capture #(xs/send svc {:type :remove-body-capture :req-idx idx :body-capture-key %})}]])

(defn request [props]
  [styled props request*])

(defn- state->items [state]
  (get-in state [:context :package :reqs]))

(defn- preamble [{:keys [state svc]}]
  [:<>
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
     "Additionally, you may reference {{params.param1}} to reference parameters
      that you expect to be passed to this request package when it's invoked."]
    [:> TextField
      {:fullWidth true
       :label "Package name"
       :value (get-in state [:context :package :name])
       :onChange #(xs/send svc {:type :update-package-name :package-name (obj/getValueByKeys % #js ["target" "value"])})}]])

(defn- -component* [{:keys [svc state]}]
  [card-list/component
   {:svc svc
    :state state
    :preamble-component preamble
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
