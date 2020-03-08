(ns webhook-explorer.components.req-card
  (:require [reagent.core :as r]
            [goog.object :as obj]
            [webhook-explorer.actions.reqs :as reqs-actions]
            [webhook-explorer.components.tag-selector :as tag-selector]
            [webhook-explorer.components.req-parts :as req-parts]
            [webhook-explorer.styles :as styles]
            ["@material-ui/core/colors" :as colors]
            ["@material-ui/core/Fab" :default FloatingActionButton]
            ["@material-ui/core/Avatar" :default Avatar]
            ["@material-ui/core/Card" :default Card]
            ["@material-ui/core/CardActions" :default CardActions]
            ["@material-ui/core/CardContent" :default CardContent]
            ["@material-ui/core/CardHeader" :default CardHeader]
            ["@material-ui/core/Tooltip" :default Tooltip]
            ["@material-ui/core/IconButton" :default IconButton]
            ["@material-ui/icons/Label" :default TagIcon]
            ["@material-ui/icons/PlaylistAdd" :default AddToCollectionIcon]
            ["@material-ui/icons/Add" :default AddIcon]
            ["@material-ui/icons/Send" :default SendIcon]
            ["@material-ui/icons/Favorite" :default FavoriteIcon]
            ["@material-ui/icons/Share" :default ShareIcon]))

(defn- background-style [theme color]
  {:color (.getContrastText (obj/get theme "palette") color)
   :backgroundColor color})

(def ^:private styled
  (styles/style-wrapper
   (fn [theme]
     (let [status-style {:width 60 :height 60 :margin 10}]
       {:card {:width "80%"
               :minWidth "480px"
               :maxWidth "768px"
               :margin "25px auto"}
        :status-info (merge
                      (background-style theme (aget colors/yellow 500))
                      status-style)
        :status-success (merge
                         (background-style theme (aget colors/green 500))
                         status-style)
        :status-redirect (merge
                          (background-style theme (aget colors/grey 500))
                          status-style)
        :status-client-error (merge
                              (background-style theme (aget colors/pink 500))
                              status-style)
        :status-server-error (merge
                              (background-style theme (aget colors/red 500))
                              status-style)
        :fix-card-content {:marginBottom "-24px"
                           :max-height 320
                           :overflow-y "auto"
                           :overflow-x "hidden"}
        :send-btn {:margin-right 15
                   :margin-bottom 15
                   :margin-left "auto"}}))))

(defn- action-btn
  ([label icon on-click]
   (action-btn label icon on-click nil))
  ([label icon on-click icon-props]
   [:> Tooltip {:title label :aria-label label}
    [:> IconButton {:aria-label label
                    :onClick on-click}
     [:> icon icon-props]]]))

(defn- tag-action-btn [{:keys [on-open-menu any-selected]}]
  [action-btn
   "Tag"
   TagIcon
   on-open-menu
   (when any-selected {:color "primary"})])

(defn- status-class [styles status]
  (cond
    (< status 200) (obj/get styles "status-info")
    (< status 300) (obj/get styles "status-success")
    (< status 400) (obj/get styles "status-redirect")
    (< status 500) (obj/get styles "status-client-error")
    :else          (obj/get styles "status-server-error")))

(defn- -component
  [{:keys [styles favorited public-tags private-tags on-visibility-toggled]
    {:keys [id
            date
            host
            path
            method
            status]
     {:keys [qs]
      {req-headers :headers
       req-cookies :cookies
       req-body :body
       {:keys [fields files]} :form} :req
      {res-headers :headers
       res-body :body} :res
      :as details} :details
     :as item} :item}]
  [:> Card {:className (obj/get styles "card")}
   [:> CardHeader
    {:avatar (r/as-element
              [:> Avatar {:aria-label status
                          :className (status-class styles status)}
               status])
     :action (r/as-element [:div
                            [action-btn "Favorite" FavoriteIcon #(reqs-actions/tag-req item {:fav true}) (when favorited {:color "secondary"})]
                            [tag-selector/component
                             {:on-select-tag (partial reqs-actions/tag-req item)
                              :rw :writable
                              :target-component tag-action-btn
                              :private-tags private-tags
                              :public-tags public-tags
                              :allow-creation true
                              :selected-label "(Already tagged)"}]
                            [action-btn "Add to request package" AddToCollectionIcon #()]
                            [action-btn "Share" ShareIcon #(reqs-actions/share-req item)]])
     :title (str method " " host path)
     :subheader date}]
   [:> CardContent {:className (obj/get styles "fix-card-content")}
    [req-parts/qs-view "Query Parameters" qs on-visibility-toggled]
    [req-parts/headers-view "Request Headers" req-headers on-visibility-toggled]
    (let [cs (or
              (some->> req-cookies
                       (map (fn [[k {:keys [value]}]] [k value]))
                       (into {}))
              (and details {}))] ; cookies might be nil, set to {} if cookies nil but we have details
      [req-parts/cookies-view
       "Request Cookies"
       cs
       on-visibility-toggled])
    [req-parts/body-view
     "Request Body"
     (req-parts/make-bodies
      {:raw {:label "Raw" :body req-body}
       :fields {:label "Form Fields" :body fields}
       :files {:label "Files" :body (when-not (empty? files) files)}})
     req-headers
     on-visibility-toggled]
    [req-parts/headers-view "Response Headers" res-headers on-visibility-toggled]
    [req-parts/body-view "Response Body" (req-parts/make-bodies {:raw {:label "Raw" :body res-body}}) res-headers on-visibility-toggled]]
   [:> CardActions
    [:> FloatingActionButton {:color "primary"
                              :className (obj/get styles "send-btn")
                              :aria-label "execute"
                              :onClick #(reqs-actions/select-item item)}
     [:> SendIcon]]]])

(defn component [params]
  [styled params -component])
