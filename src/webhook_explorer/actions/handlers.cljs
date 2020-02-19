(ns webhook-explorer.actions.handlers
  (:require [clojure.core.async :as async]
            [clojure.spec.alpha :as s]
            [webhook-explorer.http-utils :as http-utils]
            [webhook-explorer.specs.handlers]
            [webhook-explorer.app-state :as app-state]
            [webhook-explorer.promise-utils :as putil]))

(defn- get-handlers [params]
  (if (nil? params)
    (async/to-chan [:stop])
    (async/go
      (let [{{:keys [handlers nextReq]} :body} (async/<! (http-utils/req
                                                        {:method :get
                                                         :path "handlers"
                                                         :query-params params}))]
        (swap!
          app-state/handlers
          (fn [{prev-handlers :handlers :as prev}]
            (merge
              prev
              {:handlers (->> handlers
                              (concat prev-handlers)
                              (into []))
               :next-req nextReq})))
        :done))))

(def ^:private req-chan (async/chan))

(async/go-loop []
  (let [resp-chan (async/<! req-chan)
        {:keys [next-req]} @app-state/handlers
        resp (async/<! (get-handlers next-req))]
    (async/>! resp-chan resp)
    (recur)))

(defn load-next-handlers []
  (let [resp-chan (async/chan)
        p (putil/chan->promise resp-chan)]
    (async/put! req-chan resp-chan)
    p))

(defn publish-handler [handler-config]
  (async/go
    (let [res (async/<! (http-utils/req
                          {:method :post
                           :path "handlers"
                           :json-params {:handler (assoc handler-config :domain "api.easybetes.com")}}))
          {{:keys [success]} :body} res]
      (boolean success))))

(s/fdef publish-handler
  :args (s/cat :handler-config :handlers/config))
