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

(defn- request [{:keys [idx svc state class-name]
                 {:keys [req-name]
                  {header-captures :headers
                   {body-capture-type :type
                    body-captures :captures} :body} :captures
                  {:keys [protocol method host path qs headers body]} :req} :item}]
  [:> Paper {:elevation 3
             :className class-name}
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
     [:> Typography {}
      "You can use mustache-style ${templateVars} in any field"]
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

(defn- state->items [state]
  (-> state
      (obj/getValueByKeys #js ["context" "package"])
      :reqs))

(defn- -component* [{:keys [styles svc state]}]
  [card-list/component
   {:svc svc
    :state state
    :item-renderer request
    :state->items state->items
    :ready-state :ready
    :failed-state :failed
    :add-item-title "Add a request."
    :on-add-item #(xs/send svc :add-req)}])

(defn component [{:keys [styles]}]
  [xs/with-svc {:svc app-state/edit-package}
   (fn [state]
     [-component* {:svc app-state/edit-package
                   :state state}])])
