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