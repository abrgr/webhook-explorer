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

(defn- main-component []
  nil)

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
  [-component])
  ;[styled {} -component])
