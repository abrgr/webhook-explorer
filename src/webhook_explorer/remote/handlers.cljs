(ns webhook-explorer.remote.handlers
  (:require [clojure.core.async :as async]
            [clojure.spec.alpha :as s]
            [clojure.string :as string]
            [goog.object :as obj]
            [webhook-explorer.specs.handlers]
            [webhook-explorer.http-utils :as http-utils]
            [webhook-explorer.promise-utils :as putil]))

(defn adapt-handler [handler]
  (some-> handler
          (update :proto keyword)
          (update :match-type keyword)
          (update :method (comp keyword string/lower-case))
          (update
           :matchers
           (fn [ms]
             (mapv
              #(update-in % [:handler :type] keyword)
              ms)))))

(s/fdef adapt-handler
  :args (s/cat :handler map?)
  :ret :handlers/config)

(defn get-handler [params]
  (putil/chan->promise
   (async/go
     (let [ps (-> params
                  (select-keys [:proto :method :domain :match-type :path]))
           res (async/<! (http-utils/req
                          {:method :get
                           :path "handler"
                           :query-params ps}))
           {:keys [error] {:keys [handler]} :body} res
           handler (adapt-handler handler)]
       (or error handler)))))

(s/fdef get-handler
  :args (s/cat :params :handlers/get-handler-params)
  :ret #(obj/containsKey % "then"))
