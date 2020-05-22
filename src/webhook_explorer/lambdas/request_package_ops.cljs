(ns webhook-explorer.lambdas.request-package-ops
  (:require [debux.cs.core :as d :refer-macros  [dbg dbgn]]
            [clojure.core.async :as async]
            ["url" :as url]
            [webhook-explorer.request-package :as reqp]
            [webhook-explorer.lambdas.http-utils :as http]
            [webhook-explorer.utils :as u]
            [webhook-explorer.lambdas.aws :as aws]))

(defn request-package-folder-key [{:keys [name]}]
  (str "packages/" name "/"))

(defn get-request-package [rp-name]
  (let [out (async/chan)]
    (async/go
      (u/let+ [prefix (request-package-folder-key {:name rp-name})
               items (-> prefix
                         aws/s3-list-objects
                         async/<!) :abort [(instance? js/Error items) (u/put-close! out items)]
               rp-key (first items)
               rp-json (async/<! (aws/s3-get-object rp-key)) :abort [(instance? js/Error rp-json) (u/put-close! out rp-json)]]
        (-> (.parse js/JSON rp-json)
            (js->clj :keywordize-keys true)
            (->> (u/put-close! out)))))
    out))

(defn exec [{{:keys [qs body headers protocol method host path]} :req}]
  (http/request
    {:method method
     :query-params qs
     :body body
     :headers headers
     :url (url/format
            #js {:protocol protocol
                 :hostname host
                 :pathname path
                 :query (clj->js qs)})}))

(defn execute [rp inputs]
  (reqp/run-pkg
    {:inputs inputs
     :exec exec
     :pkg rp}))
