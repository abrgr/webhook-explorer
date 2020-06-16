(ns webhook-explorer.actions.reqs
  (:require [webhook-explorer.app-state :as app-state]
            [webhook-explorer.promise-utils :as putil]
            [webhook-explorer.actions.auth :as auth-actions]
            [webhook-explorer.remote.s3-load :as s3-load]
            [webhook-explorer.actions.tags :as tag-actions]
            [webhook-explorer.http-utils :as http-utils]
            [webhook-explorer.routes :as routes]
            [webhook-explorer.init :as init]
            [goog.object :as obj]
            [cljs-http.client :as http]
            [clojure.core.async :as async]
            [clojure.set :as s]
            [clojure.string :as string]
            ["copy-to-clipboard" :as copy-to-clipboard]))

(defn- make-next-req [{:keys [all fav tag pub]} latest-date]
  (merge
   (when all
     {})
   (when tag
     {:tag tag})
   (when pub
     {:pub true})
   (when latest-date
     {:ymd latest-date})
   (when fav
     {:fav fav})))

(defn- init! []
  (add-watch
   app-state/nav
   ::nav-watch
   (fn [_ _ {old-params :params} {new-params :params :keys [page]}]
     (when (and (= page :reqs)
                (not= old-params new-params))
       (let [new-latest-date (:latest-date new-params)
             latest-date (when (some? new-latest-date)
                           (string/replace new-latest-date "-" "/"))
             next-req (make-next-req new-params latest-date)
             old-req (-> @app-state/reqs
                         :next-req
                         (select-keys [:latest-date :all :fav :pub :tag]))]
         (swap!
          app-state/reqs
          merge
          {:items []
           :next-req next-req
           :tagged-reqs {}
           :earliest-tagged-req nil
           :next-tagged-req {}}))))))

(init/register-init 0 init!)

(defn- get-tagged-reqs-thru [earliest-item-date]
  (async/go-loop []
    (let [{:keys [next-tagged-req]} @app-state/reqs]
      (when-not (nil? next-tagged-req)
        (let [res (async/<! (http-utils/req
                             {:method :get
                              :path "tagged-reqs"
                              :literal-res-paths #{[:tagsByFingerprint]}
                              :query-params next-tagged-req}))
              {{:keys [earliest-date tags-by-fingerprint next-req]} :body} res]
          (swap!
           app-state/reqs
           (fn [{prev-tagged-reqs :tagged-reqs :as reqs}]
             (let [tagged-reqs (->> tags-by-fingerprint
                                    (reduce
                                     (fn [tagged-reqs [fingerprint {:keys [fav private-tags public-tags]}]]
                                       (let [prev (get tagged-reqs (name fingerprint))
                                             fav (or (:fav prev) fav)
                                             private-tags (->> prev
                                                               :private-tags
                                                               (concat private-tags)
                                                               (into #{}))
                                             public-tags (->> prev
                                                              :public-tags
                                                              (concat public-tags)
                                                              (into #{}))]
                                         (assoc
                                          tagged-reqs
                                          (name fingerprint)
                                          {:fav fav
                                           :private-tags private-tags
                                           :public-tags public-tags})))
                                     prev-tagged-reqs))]
               (merge
                reqs
                {:tagged-reqs tagged-reqs
                 :next-tagged-req next-req
                 :earliest-tagged-req earliest-date}))))
          (when (and (some? next-req)
                     (< earliest-item-date earliest-date))
            (recur)))))))

(defn- get-reqs [params]
  (if (nil? params)
    (async/to-chan [:stop])
    (async/go
      (let [{{:keys [items next-req]} :body} (async/<! (http-utils/req
                                                        {:method :get
                                                         :path "reqs"
                                                         :query-params params}))
            earliest-item-date (-> items last :date)
            earliest-tag-req-date (:earliest-tagged-req @app-state/reqs)]
        (when (or (nil? earliest-tag-req-date)
                  (< earliest-item-date earliest-tag-req-date))
          (get-tagged-reqs-thru earliest-item-date))
        (swap!
         app-state/reqs
         (fn [{prev-items :items :as reqs}]
           (merge
            reqs
            {:items (->> items
                         (concat prev-items)
                         (into []))
             :next-req next-req})))
        :done))))

(defn- to-str-key [m]
  (->> m
       (map (fn [[k v]] [(name k) v]))
       (into {})))

(defn- fix-req-details [req-details]
  (-> req-details
      (update-in [:req :headers] to-str-key)
      (update-in [:res :headers] to-str-key)))

(defn- load-full-req [{:keys [id data-url] :as item}]
  (async/go
    (let [{req-details :body} (async/<!
                               (s3-load/request {:method :get :url data-url :with-credentials? false}))
          req-details (fix-req-details req-details)]
      (swap!
       app-state/reqs
       update
       :items
       (fn [items]
         (->> items
              (mapv #(if (= (:id %) id) (assoc % :details req-details) %)))))
      (assoc item :details req-details))))

(def ^:private req-chan (async/chan))

(async/go-loop []
  (let [resp-chan (async/<! req-chan)
        {:keys [next-req]} @app-state/reqs
        resp (async/<! (get-reqs next-req))]
    (async/>! resp-chan resp)
    (recur)))

(defn load-next-items []
  (let [resp-chan (async/chan)
        p (putil/chan->promise resp-chan)]
    (async/put! req-chan resp-chan)
    p))

(defn with-full-item [item f]
  (async/go
    (let [item' (if (:details item)
                  item
                  (->> item
                       load-full-req
                       async/<!))]
      (f item'))))

(defn select-item [item]
  (with-full-item
    item
    #(swap!
      app-state/reqs
      assoc-in
      [:selected-item :item]
      (-> %
          (dissoc :status)
          (update-in [:details] dissoc :res)))))

(defn- selected-req []
  (let [{{:keys [item]} :selected-item} @app-state/reqs
        {:keys [method path]} item
        host (get-in item [:details :host])
        protocol (get-in item [:details :protocol])
        headers (get-in item [:details :req :headers])
        body (get-in item [:details :req :body])
        q-params (get-in item [:details :qs])
        q (if (empty? q-params)
            ""
            (->> q-params
                 (map (fn [[k v]]
                        (str
                         (js/encodeURIComponent (name k))
                         "="
                         (js/encodeURIComponent (name v)))))
                 (interpose "&")
                 (apply str)
                 (str "?")))
        url (str protocol "://" host path q)]
    {:method method
     :url url
     :headers (->> headers
                   (map #(vector (name (first %)) (second %)))
                   (into {}))
     :body body}))

(defn update-selected-item-in [item-ks value]
  (swap!
   app-state/reqs
   assoc-in
   (concat [:selected-item] item-ks)
   value))

(defn unselect-item []
  (swap!
   app-state/reqs
   assoc
   :selected-item
   nil))

(defn tag-req [{:keys [fingerprint] :as item} {:keys [fav pub tag] :as opts}]
  (tag-actions/add-tag opts)
  (with-full-item
    item
    #(async/go
       (let [{:keys [date path host method status]
              {:keys [protocol qs iso]
               {req-headers :headers
                req-body :body
                req-cookies :cookies
                req-form :form} :req
               {res-headers :headers
                res-body :body} :res} :details} %
             {:keys [success]} (async/<!
                                (http-utils/req
                                 {:method :post
                                  :path "tagged-reqs"
                                  :literal-req-paths #{[:req :req :headers]
                                                       [:req :req :cookies]
                                                       [:req :req :form :fields]
                                                       [:req :res :headers]
                                                       [:req :qs]}
                                  :query-params opts
                                  :json-params
                                  {:req {:host host
                                         :protocol protocol
                                         :path path
                                         :qs qs
                                         :method method
                                         :iso iso
                                         :status (js/parseInt status 10)
                                         :req {:headers req-headers
                                               :body req-body
                                               :cookies req-cookies
                                               :form req-form}
                                         :res {:headers res-headers
                                               :body res-body}}}}))]
         (apply
          swap!
          app-state/reqs
          update-in
          [:tagged-reqs
           fingerprint
           (cond fav :fav
                 pub :public-tags
                 :else :private-tags)]
          (if fav
            [(constantly true)] ; set :fav to true
            [s/union #{tag}])) ; union :public-tags or :private-tags with #{tag}
         success))))

(defn set-latest-date [moment]
  (let [params (:params @app-state/nav)]
    (routes/nav-to-reqs
     {:query-params
      (assoc
       params
       :latest-date
       (.format moment "YYYY-MM-DD"))})))

(defn select-tag [selected-tag]
  (let [params (:params @app-state/nav)]
    (routes/nav-to-reqs
     {:query-params
      (merge
       (select-keys params [:latest-date])
       selected-tag)})))

(defn- req->curl-string [{:keys [headers method body url]}]
  (let [header-parts (->> headers
                          (map
                           (fn [[header value]]
                             (str "--header '" header  ": " value "'")))
                          (interpose " ")
                          (apply str))]
    (str "curl"
         " "
         "--request " method
         " "
         header-parts
         " "
         (when (some? body)
           (str "--data '" body "'"
                " "))
         "'" url "'")))

(defn copy-selected-as-curl []
  (let [opts (selected-req)
        curl (req->curl-string opts)
        clipboard-result (copy-to-clipboard curl)]
    (swap!
     app-state/reqs
     assoc-in
     [:selected-item :notification]
     "Copied curl command to clipboard")))

(defn- sending-req [get-res]
  (let [opts (selected-req)]
    (async/go
      (swap!
       app-state/reqs
       update
       :selected-item
       (fn [item]
         (-> item
             (assoc-in [:in-progress] true)
             (dissoc :status)
             (update-in [:details] dissoc :res))))
      (let [res (async/<! (get-res opts))
            iso (.toISOString (js/Date.))]
        (swap!
         app-state/reqs
         update
         :selected-item
         (fn [{{:keys [details] :as item} :item :as selected-item}]
           (-> selected-item
               (assoc :in-progress false)
               (assoc-in [:item :iso] iso)
               (assoc-in [:item :details :iso] iso)
               (assoc-in
                [:item :details :res]
                {:headers (:headers res)
                 :body (:body res)})
               (assoc-in [:item :status] (:status res)))))
        (tag-req (get-in @app-state/reqs [:selected-item :item]) {:tag "My Executed Requests"})))))

(defn send-selected-as-local-request []
  (letfn [(get-res [opts]
            (async/go
              (async/<! (http/request (assoc opts
                                             :with-credentials?
                                             false)))))]
    (sending-req get-res)))

(defn send-selected-as-remote-request []
  (letfn [(get-res [opts]
            (async/go
              (get-in
               (async/<! (http-utils/req {:method :post
                                          :path (str "execute-req")
                                          :json-params {:req opts}}))
               [:body :res])))]
    (sending-req get-res)))

(defn share-req [{:keys [data-url]}]
  (let [[_ slug] (re-find #"://[^/]+/([^?]+)[?]?" data-url)
        share-url (->> {:slug (js/encodeURIComponent slug)}
                       routes/req-path
                       (str (obj/getValueByKeys js/window #js ["location" "origin"])))]
    (copy-to-clipboard share-url)
    (swap!
     app-state/reqs
     assoc-in
     [:selected-item :notification]
     "Copied sharable URL to clipboard")))

(defn load-req [slug]
  (async/go
    (let [res (async/<! (http-utils/req
                         {:method :get
                          :path (str "reqs/" slug)
                          :literal-res-paths #{[:details :req :headers]
                                               [:details :res :headers]
                                               [:details :req :form :fields]
                                               [:details :res :form :fields]}}))
          {item :body} res
          adj-item (-> item
                       (update-in [:tags :private-tags] set)
                       (update-in [:tags :public-tags] set)
                       (update :details fix-req-details))]
      adj-item)))
