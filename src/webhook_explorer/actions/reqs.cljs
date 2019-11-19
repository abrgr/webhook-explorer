(ns webhook-explorer.actions.reqs
  (:require [webhook-explorer.app-state :as app-state]
            [webhook-explorer.promise-utils :as putil]
            [cljs-http.client :as http]
            [clojure.core.async :as async]))

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
              {:items (->> items (concat prev-items) (into []))
               :in-progress-req nil
               :next-req nextReq})))
        :done))))

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
