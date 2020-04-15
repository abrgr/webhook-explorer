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
            [webhook-explorer.components.card-list :as card-list]
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

(defn- request []
  "Request")

(defn- -component* [{:keys [styles svc state]}]
  [card-list/component
   {:svc svc
    :state state
    :item-renderer request
    :state->items (constantly [])
    :ready-state :ready
    :failed-state :failed
    :add-item-title "Add a request."
    :on-add-item #(xs/send svc :add-request)}])

(defn component [{:keys [styles]}]
  [xs/with-svc {:svc app-state/edit-package}
   (fn [state]
     [-component* {:svc app-state/edit-package
                   :state state}])])
