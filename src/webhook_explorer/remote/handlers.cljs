(ns webhook-explorer.remote.handlers
  (:require [clojure.core.async :as async]
            [clojure.spec.alpha :as s]
            [webhook-explorer.specs.handlers]
            [webhook-explorer.http-utils :as http-utils]
            [webhook-explorer.promise-utils :as putil]))

(defn get-handler [params]
  (putil/chan->promise
   (async/go
     (let [ps (-> params
                  (select-keys [:proto :method :domain :match-type :path]))
           res (async/<! (http-utils/req
                          {:method :get
                           :path "handler"
                           :query-params ps}))
           {:keys [error] {:keys [handler]} :body} res]
       (or error handler)))))

(s/fdef get-handler
  :args (s/cat :params :handlers/get-handler-params))
