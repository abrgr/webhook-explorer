(ns webhook-explorer.containers.users
  (:require [clojure.core.async :as async]
            [reagent.core :as r]
            [goog.object :as obj]
            [webhook-explorer.app-state :as app-state]
            [webhook-explorer.styles :as styles]
            [webhook-explorer.actions.users :as users-actions]
            ["moment" :as moment]
            ["@material-ui/core/TableCell" :default TableCell]
            ["@material-ui/core/Button" :default Button]
            ["@material-ui/core/Dialog" :default Dialog]
            ["@material-ui/core/DialogActions" :default DialogActions]
            ["@material-ui/core/DialogContent" :default DialogContent]
            ["@material-ui/core/DialogContentText" :default DialogContentText]
            ["@material-ui/core/DialogTitle" :default DialogTitle]
            ["@material-ui/core/InputLabel" :default InputLabel]
            ["@material-ui/core/MenuItem" :default MenuItem]
            ["@material-ui/core/FormControlLabel" :default FormControlLabel]
            ["@material-ui/core/FormControl" :default FormControl]
            ["@material-ui/core/FormHelperText" :default FormHelperText]
            ["@material-ui/core/Select" :default Select]
            ["@material-ui/core/TextField" :default TextField]
            ["react-virtualized/dist/commonjs/AutoSizer" :default AutoSizer]
            ["react-virtualized/dist/commonjs/Table" :default Table]
            ["react-virtualized/dist/commonjs/Table/Column" :default Column]
            ["react-virtualized/dist/commonjs/InfiniteLoader" :default InfiniteLoader]))

(def ^:private styled
  (styles/style-wrapper
    (fn [theme]
      {:flex-container {:display "flex"
                        :align-items "center"}
       :no-outline {:outline "none"}
       :container {:width "80%"
                   :height "100%"
                   :minWidth "480px"
                   :maxWidth "768px"
                   :margin "25px auto"}
       :right-align {:width "100%"
                     :display "flex"
                     :justify-content "flex-end"}})))

(def ^:private row-height 48)

(def ^:private cols
  (sorted-map
    :email {:label "Email"}
    :enabled {:label "Enabled"}
    :role {:label "Role"}))

(defn- header-renderer [props]
  (r/as-element
    [:> TableCell
      {:component "div"
       :variant "head"
       :style #js {:height row-height :display "flex" :flex 1 :alignItems "center"}}
      [:span (obj/get props "label")]]))

(defn- cell-renderer [props]
  (r/as-element
    [:> TableCell
      {:component "div"
       :variant "body"
       :style #js {:height row-height :display "flex" :flex 1 :alignItems "center"}}
      (obj/get props "cellData" "")]))

(defn- get-cell-data [props]
  (let [row-data (obj/get props "rowData")
        data-key (obj/get props "dataKey")]
    (str (get row-data (keyword data-key)))))

(defn- load-more-rows []
  (users-actions/load-next-users))

(defn- user-list [{:keys [styles users next-req]}]
  (let [row-count (if (nil? next-req) (count users) (inc (count users)))]
    [:> AutoSizer
      (fn [size]
        (let [height (obj/get size "height")
              width (obj/get size "width")]
          (r/as-element
            [:> InfiniteLoader {:isRowLoaded #(or (nil? next-req) (< (obj/get % "index") (count users)))
                                :threshold 5
                                :minimumBatchSize 10
                                :loadMoreRows load-more-rows
                                :rowCount row-count
                                :height height}
              (fn [scroll-info]
                (r/as-element
                  [:> Table
                    {:ref #((obj/get scroll-info "registerChild") %)
                     :gridClassName (obj/get styles "no-outline")
                     :height height
                     :width width
                     :rowClassName (obj/get styles "flex-container")
                     :onRowsRendered (obj/get scroll-info "onRowsRendered")
                     :rowCount row-count
                     :rowHeight row-height
                     :headerHeight row-height
                     :rowGetter #(get-in @app-state/users [:users (obj/get % "index")])}
                    (for [[col {:keys [label]}] cols]
                      ^{:key col}
                      [:> Column
                        {:headerRenderer header-renderer
                         :cellRenderer cell-renderer
                         :cellDataGetter get-cell-data
                         :label label
                         :flexGrow 1
                         :width 120
                         :dataKey col}])]))])))]))

(defn- user-dialog [{:keys [user on-close on-update]}]
  [:> Dialog {:open (some? user)
              :onClose on-close
              :fullWidth true
              :PaperProps #js {:style #js {"height" "75%"}}}
    [:> DialogTitle "Create User"]
    [:> DialogContent
      [:> DialogContentText
        "Create a new user. They will receive an email with temporary credentials and instructions to sign in."]
      [:> FormControl {:fullWidth true
                       :margin "normal"}
        [:> TextField {:fullWidth true
                       :label "Email"
                       :type "email"
                       :value (get user :email "")
                       :onChange #(let [v (obj/getValueByKeys % #js ["target" "value"])]
                                    (on-update assoc :email v))}]]
      [:> FormControl {:fullWidth true
                       :margin "normal"}
        [:> InputLabel "Role"]
        [:> Select {:value (get user :role "")
                    :onChange #(let [v (obj/getValueByKeys % #js ["target" "value"])]
                                 (on-update assoc :role v))}
          [:> MenuItem {:value "admin"} "Admin"]
          [:> MenuItem {:value "eng"} "Engineer"]
          [:> MenuItem {:value "ops"} "Ops"]]
        [:> FormHelperText
          "Ops users can only use templated requests but cannot view or execute other requests. Engineer users can do everything except manage and create other users. Admin users can do everything, including managing other users."]]]
    [:> DialogActions
      [:> Button {:onClick on-close}
        "Cancel"]
      [:> Button {:onClick on-close
                  :color "primary"} 
        "Create"]]])

(defn- -component []
  (let [editing-user (r/atom nil)]
    (fn [{:keys [styles]}]
      (let [{:keys [users next-req]} @app-state/users
            row-count (if (nil? next-req) (count users) (inc (count users)))]
        [:div {:className (obj/get styles "container")}
          [:div {:className (obj/get styles "right-align")}
            [:> Button {:variant "contained"
                        :color "primary"
                        :onClick #(reset! editing-user {})}
              "Create user"]]
          [user-dialog {:user @editing-user
                        :on-close #(reset! editing-user nil)
                        :on-update (partial swap! editing-user)}]
          [user-list {:styles styles :users users :next-req next-req}]]))))

(defn component []
  [styled {} -component])
