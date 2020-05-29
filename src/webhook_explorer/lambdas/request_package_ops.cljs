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

(defn execution-sets-folder-prefix [package-name]
  (str "execution-sets/" package-name "/"))

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
                  (assoc :key rp-key)
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

(defn make-execution-set-id [uid]
  (str (u/descending-s3-date) "|" uid "|" (random-uuid)))

(defn read-execution-set-id [id]
  (-> id
      (string/split #"[|]")
      (->> (zipmap [:descending-date :date :uid :id]))))

(defn list-request-packages [{:keys [token]}]
  (u/async-xform
   (map
    (u/pass-errors
     (fn [{:keys [items next-token]}]
       {:request-packages (mapv (partial assoc nil :name) items)
        :next-token next-token})))
   (list-items {:token token :prefix package-folder-prefix})))

(defn list-execution-sets [{:keys [request-package-name token]}]
  (u/async-xform
   (map
    (u/pass-errors
     (fn [{:keys [items next-token]}]
       {:execution-sets (mapv #(-> %
                                   (string/replace #"^.*[/]" "")
                                   read-execution-set-id
                                   (dissoc :descending-date)) items)
        :next-token next-token})))
   (list-items {:token token
                :prefix (execution-sets-folder-prefix request-package-name)})))

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

(defn write-execution-set [{:keys [request-package uid inputs]}]
  (let [id (make-execution-set-id uid)
        k (str (execution-sets-folder-prefix (:name request-package)) id "/requests/")]
    (->> inputs
         (map-indexed
          (fn [idx input]
            (let [this-key (str k idx)]
              {:body (js/JSON.stringify #js {:execution-request-key this-key})
               :key this-key
               :path "execute-request-package"
               :data (-> {:uid uid
                          :request-package-key (:key request-package)
                          :inputs input}
                         clj->js
                         js/JSON.stringify)})))
         (map
          (fn [msg]
            (let [c (async/chan)]
              (async/go
                (async/<! (aws/s3-put-object
                           {:key (:key msg)
                            :content-type "application/json"
                            :body (:data msg)}))
                (u/put-close! c msg))
              c)))
         async/merge
         (async/into [])
         (u/async-xform
          (map
           (u/pass-errors aws/sqs-put-all)))
         u/async-unwrap
         (u/async-xform
          (map
           (u/pass-errors
            (fn [results]
              {:id id})))))))

(defn execute [rp inputs]
  (reqp/run-pkg
   {:inputs inputs
    :exec exec
    :pkg rp}))
