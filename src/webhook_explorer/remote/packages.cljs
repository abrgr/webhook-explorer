(ns webhook-explorer.remote.packages
  (:require [clojure.core.async :as async]
            [clojure.spec.alpha :as s]
            [clojure.string :as string]
            [goog.object :as obj]
            [webhook-explorer.specs.handlers]
            [webhook-explorer.http-utils :as http-utils]
            [webhook-explorer.promise-utils :as putil]))

(defn save-package [pkg]
  (putil/chan->promise
   (async/go
     (let [res (async/<! (http-utils/req
                          {:method :post
                           :path "request-packages"
                           :json-params {:package pkg}}))
           {:keys [error] {:keys [success]} :body} res]
       (or error (when-not success (js/Error. "Failure")))))))

(defn load-packages [params]
  (putil/chan->promise
   (async/go
     (let [res (async/<! (http-utils/req
                          {:method :get
                           :path "request-packages"
                           :query-params (select-keys params [:token])}))
           {:keys [error body]} res]
       (or
        error
        (when-not (:request-packages body) (js/Error. "Failure"))
        body)))))

(defn load-package [{:keys [name]}]
  (putil/chan->promise
   (async/go
     (let [res (async/<! (http-utils/req
                          {:method :get
                           :path (str "request-packages/" (js/encodeURIComponent name))}))
           {:keys [error] {:keys [request-package]} :body} res]
       (or
        error
        (when-not request-package (js/Error. "Failure"))
        request-package)))))

(defn load-executions [rp-name params]
  (putil/chan->promise
   (async/go
     (let [res (async/<! (http-utils/req
                          {:method :get
                           :path (str "request-packages/" (js/encodeURIComponent rp-name) "/executions")
                           :query-params (select-keys params [:token])}))
           {:keys [error body]} res]
       (or
        error
        (when-not (:execution-sets body) (js/Error. "Failure"))
        body)))))

(defn execute [rp-name params]
  (putil/chan->promise
   (async/go
     (let [res (async/<! (http-utils/req
                          {:method :post
                           :path (str "request-packages/" (js/encodeURIComponent rp-name) "/executions")
                           :json-params {:request-package-execution (select-keys params [:inputs])}}))
           {:keys [error] {:keys [request-package-execution]} :body} res]
       (or
        error
        (when-not request-package-execution (js/Error. "Failure"))
        request-package-execution)))))

(defn load-execution-set-executions [rp-name execution-id params]
  (putil/chan->promise
   (async/go
     (let [res (async/<! (http-utils/req
                          {:method :get
                           :path (str "request-packages/" (js/encodeURIComponent rp-name) "/executions/" (js/encodeURIComponent execution-id))
                           :query-params (select-keys params [:token])}))
           {:keys [error body]} res]
       (or
        error
        (when-not (:executions body) (js/Error. "Failure"))
        body)))))
