(ns webhook-explorer.actions.reqs
  (:require [webhook-explorer.app-state :as app-state]
            [webhook-explorer.promise-utils :as putil]
            [cljs-http.client :as http]
            [clojure.core.async :as async]
            [clojure.set :as s]
            ["copy-to-clipboard" :as copy-to-clipboard]))

(defn- get-reqs [params]
  (if (nil? params)
    (async/to-chan [:stop])
    (async/go
      (let [{{:keys [items nextReq]} :body} (async/<! (http/get
                                                        "https://webhook-explorer.easybetes.com/api/reqs"
                                                        {:with-credentials? false
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
               :in-progress-req nil
               :next-req nextReq})))
        :done))))

(defn- load-full-req [{:keys [id data-url]}]
  (async/go
    (let [{req-details :body} (async/<!
                                (http/get data-url {:with-credentials? false}))]
      (swap!
        app-state/reqs
        update
        :items
        (fn [items]
          (->> items
               (mapv #(if (= (:id %) id) (assoc % :details req-details) %))))))))

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

(defn select-item [item]
  (async/go
    (let [item' (if (:details item)
                  item
                  (->> item
                       load-full-req
                       async/<!
                       :items
                       (filter #(= (:id %) (:id item)))
                       first))]
      (swap!
        app-state/reqs
        assoc
        :selected-item
        {:item item'}))))

(defn- selected-req []
  (let [{{:keys [item]} :selected-item} @app-state/reqs
        {:keys [method path]} item
        protocol (get-in item [:details :protocol])
        headers (get-in item [:details :req :headers])
        body (get-in item [:details :req :body])
        url (str protocol "://" (get headers :Host) path)]
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
      (let [res (async/<! (http/request (assoc selected-req
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
