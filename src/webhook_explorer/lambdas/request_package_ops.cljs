(ns webhook-explorer.lambdas.request-package-ops
  (:require [debux.cs.core :as d :refer-macros  [dbg dbgn]]
            [clojure.core.async :as async]
            [clojure.string :as string]
            ["url" :as url]
            [webhook-explorer.request-package :as reqp]
            [webhook-explorer.lambdas.http-utils :as http]
            [webhook-explorer.utils :as u]
            [webhook-explorer.lambdas.aws :as aws]))

(def package-folder-prefix "packages/")

(defn request-package-folder-key [{:keys [name]}]
  (str package-folder-prefix name "/"))

(defn get-request-package [rp-name]
  (let [out (async/chan)]
    (async/go
      (u/let+ [prefix (request-package-folder-key {:name rp-name})
               {:keys [items] :as r} (-> {:prefix prefix}
                                         aws/s3-list-objects
                                         async/<!) :abort [(instance? js/Error r) (u/put-close! out r)]
               rp-key (first items)
               rp-json (async/<! (aws/s3-get-object rp-key)) :abort [(instance? js/Error rp-json) (u/put-close! out rp-json)]]
              (-> (.parse js/JSON rp-json)
                  (js->clj :keywordize-keys true)
                  (->> (u/put-close! out)))))
    out))

(defn list-request-packages [{:keys [token]}]
  (let [out (async/chan)]
    (async/go
      (u/let+ [{:keys [items next-token]
                :as r} (-> {:prefix package-folder-prefix :token token}
                           aws/s3-list-objects
                           async/<!) :abort [(instance? js/Error r) (u/put-close! out r)]]
              (u/put-close!
               out
               {:request-packages (mapv
                                   (fn [n]
                                     {:name (-> n
                                                (string/replace-first package-folder-prefix "")
                                                (string/replace #"/$" ""))})
                                   items)
                :next-token next-token})))
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
