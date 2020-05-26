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

(defn executions-folder-prefix [package-name]
  (str "executions/" package-name "/"))

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

(defn list-items [{:keys [token prefix]}]
  (let [out (async/chan)]
    (async/go
      (u/let+ [{:keys [items next-token]
                :as r} (-> {:prefix prefix :token token}
                           aws/s3-list-objects
                           async/<!) :abort [(instance? js/Error r) (u/put-close! out r)]]
              (u/put-close!
               out
               {:items (map
                         (fn [n]
                           (-> n
                               (string/replace-first package-folder-prefix "")
                               (string/replace #"/$" "")))
                         items)
                :next-token next-token})))
    out))

(defn list-request-packages [{:keys [token]}]
  (u/async-xform
    (map
      (u/pass-errors
        (fn [{:keys [items next-token]}]
          {:request-packages (mapv (partial assoc nil :name) items)
           :next-token next-token})))
    (list-items {:token token :prefix package-folder-prefix})))

(defn list-request-package-executions [{:keys [request-package-name token]}]
  (u/async-xform
    (map
      (u/pass-errors
        (fn [{:keys [items next-token]}]
          {:request-package-executions (mapv (partial assoc nil :id) items)
           :next-token next-token})))
    (list-items {:token token
                 :prefix (executions-folder-prefix request-package-name)})))

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

(defn write-execution [{:keys [request-package-name uid inputs]}]
  (let [id (str (random-uuid))  
        k (str (executions-folder-prefix request-package-name) id)]
    (u/async-xform-all
      (map
        (u/pass-errors
          (constantly {:id id})))
      (aws/s3-put-object {:key k
                          :content-type "application/json"
                          :body (-> {:uid uid :inputs inputs}
                                    clj->js
                                    js/JSON.stringify)}))))

(defn execute [rp inputs]
  (reqp/run-pkg
   {:inputs inputs
    :exec exec
    :pkg rp}))
