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

(defn execution-set-executions-prefix [package-name execution-set-id]
  (str (execution-sets-folder-prefix package-name)
       execution-set-id
       "/executions/"))

(defn execution-prefix->execution-request-key [execution-prefix]
  (str execution-prefix "request"))

(defn execution-prefix->execution-result-key [execution-prefix]
  (str execution-prefix "result"))

(defn execution-request-key->execution-result-key [execution-request-key]
  (string/replace execution-request-key #"/[^/]+$" "/result"))

(defn request-package-folder-key [{:keys [name]}]
  (str package-folder-prefix name "/"))

(defn get-request-package-by-key [rp-key]
  (->> (aws/s3-get-object rp-key)
       (u/async-xform
        (map
         (u/pass-errors
          (fn [rp-json]
            (-> rp-json
                u/json->kebab-clj
                (assoc :key rp-key))))))))

(defn get-request-package [rp-name]
  (let [out (async/chan)]
    (async/go
      (u/let+ [prefix (request-package-folder-key {:name rp-name})
               {:keys [items] :as r} (-> {:prefix prefix}
                                         aws/s3-list-objects
                                         async/<!) :abort [(instance? js/Error r) (u/put-close! out r)]
               rp-key (first items)]
              (-> rp-key
                  get-request-package-by-key
                  (async/pipe out))))
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

(defn make-execution-set-id
  ([uid]
   (make-execution-set-id uid (js/Date.) (random-uuid)))
  ([uid date id]
   (str (u/descending-s3-date (if (string? date) (js/Date. date) date))
        "|"
        uid
        "|"
        id)))

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
       {:execution-sets (mapv
                         (fn [k]
                           (-> k
                               (string/replace #"^.*[/]" "")
                               (#(-> %
                                     read-execution-set-id
                                     (assoc :execution-set-id %)))
                               (dissoc :descending-date)))
                         items)
        :next-token next-token})))
   (list-items {:token token
                :prefix (execution-sets-folder-prefix request-package-name)})))

(defn write-execution-set [{:keys [request-package uid inputs]}]
  (let [id (make-execution-set-id uid)
        k (execution-set-executions-prefix (:name request-package) id)]
    (->> inputs
         (map-indexed
          (fn [idx input]
            (let [this-key (str k idx "/request")]
              {:body (js/JSON.stringify #js {:execution-request-key this-key})
               :key this-key
               :path "execute-request-package"
               :data (u/clj->camel-json
                      {:uid uid
                       :request-package-key (:key request-package)
                       :inputs input})})))
         (map
          (fn [msg]
            (->> (aws/s3-put-object
                  {:key (:key msg)
                   :content-type "application/json"
                   :body (:data msg)})
                 (u/async-xform
                  (map
                   (u/pass-errors (constantly msg)))))))
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

(defn write-execution-result [{:keys [execution-request-key result]}]
  (let [result-key (execution-request-key->execution-result-key execution-request-key)]
    (aws/s3-put-object
     {:key result-key
      :content-type "application/json"
      :body (u/clj->camel-json result)})))

(defn list-execution-set-executions [{:keys [request-package-name
                                             execution-set-id
                                             token]}]
  (->> (list-items {:token token
                    :prefix (execution-set-executions-prefix
                             request-package-name
                             execution-set-id)})
       (u/async-xform
        (map
         (u/pass-errors
          (fn [{:keys [items next-token]}]
            (->> items
                 (map
                  (fn [execution-prefix]
                    (let [prefix (str execution-prefix "/")
                          c (async/chan)
                          request-url-c (aws/s3-signed-get-object
                                         {:key (execution-prefix->execution-request-key prefix)})
                          result-url-c (aws/s3-signed-get-object
                                        {:key (execution-prefix->execution-result-key prefix)})]
                      (async/go
                        (let [result {:request-url (async/<! request-url-c)
                                      :result-url (async/<! result-url-c)}]
                          (u/put-close! c result)))
                      c)))
                 async/merge
                 (u/async-xform-all
                  (map
                   (fn [executions]
                     {:executions executions
                      :next-token next-token}))))))))
       u/async-unwrap))

(defn get-execution-request [execution-request-key]
  (->> (aws/s3-get-object execution-request-key)
       (u/async-xform
        (map
         (u/pass-errors u/json->kebab-clj)))))

(defn exec [{{:keys [qs body headers protocol method host path]} :req :as req}]
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

(defn execute [{:keys [rp inputs]}]
  (->> (reqp/run-pkg
        {:inputs inputs
         :exec exec
         :pkg rp})
       (u/async-xform-all
        (map
         (u/pass-errors (constantly {:results {:success true}}))))))
