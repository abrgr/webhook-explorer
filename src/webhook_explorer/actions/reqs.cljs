(ns webhook-explorer.actions.reqs
  (:require [webhook-explorer.app-state :as app-state]
            [webhook-explorer.promise-utils :as putil]
            [webhook-explorer.actions.auth :as auth-actions]
            [webhook-explorer.actions.tags :as tag-actions]
            [webhook-explorer.http-utils :as http-utils]
            [cljs-http.client :as http]
            [clojure.core.async :as async]
            [clojure.set :as s]
            ["copy-to-clipboard" :as copy-to-clipboard]))
    
(defn- get-reqs [params]
  (if (nil? params)
    (async/to-chan [:stop])
    (async/go
      (let [{{:keys [items nextReq]} :body} (async/<! (http/get
                                                        (http-utils/make-url "/api/reqs")
                                                        {:with-credentials? false
                                                         :headers (http-utils/auth-headers)
                                                         :query-params params}))]
        (swap!
          app-state/reqs 
          (fn [{prev-items :items :as reqs}]
            (merge
              reqs
              {:items (->> items
                           (map #(s/rename-keys % {:dataUrl :data-url}))
                           (concat prev-items)
                           (into []))
               :next-req nextReq})))
        :done))))

(defn- load-full-req [{:keys [id data-url] :as item}]
  (async/go
    (let [{req-details :body} (async/<!
                                (http/get data-url {:with-credentials? false}))]
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
       assoc
       :selected-item
       {:item %})))

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

(defn send-selected-as-local-request []
  (let [opts (selected-req)]
    (async/go
      (swap!
        app-state/reqs
        assoc-in
        [:selected-item :in-progress]
        true)
      (let [res (async/<! (http/request (assoc opts
                                               :with-credentials?
                                               false)))]
        (swap!
          app-state/reqs
          update
          :selected-item
          #(merge
             %
             {:in-progress false
              :res res}))))))

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

(defn tag-req [item opts]
  (tag-actions/add-tag opts)
  (with-full-item
    item
    #(async/go
       (let [{:keys [date path host method]
              {:keys [protocol qs iso]
               {req-headers :headers
                req-body :body} :req
               {res-headers :headers
                res-body :body} :res} :details} %
             {:keys [success]} (async/<!
                                 (http/post
                                   (http-utils/make-url "/api/tag-req")
                                   {:with-credentials? false
                                    :headers (http-utils/auth-headers)
                                    :query-params opts
                                    :json-params
                                    {:req {:host host
                                           :protocol protocol
                                           :path path
                                           :qs qs
                                           :method method
                                           :iso iso
                                           :req {:headers req-headers
                                                 :body req-body}
                                           :res {:headers res-headers
                                                 :body res-body}}}}))]
         success))))
