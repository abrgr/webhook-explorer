(ns webhook-explorer.lambdas.request-package-ops
  (:require [clojure.core.async :as async]
            [webhook-explorer.utils :as u]
            [webhook-explorer.lambdas.aws :as aws]))

(defn request-package-folder-key [{:keys [name]}]
  (str "packages/" name))

(defn get-request-package [rp-name]
  (async/go
    (u/let+ [out (async/chan)
             prefix (request-package-folder-key {:name rp-name})
             items (-> prefix
                       aws/s3-list-objects
                       async/<!
                       first) :abort [(instance? js/Error items) (do (u/put-close! out items) out)]
             rp-key (first items)
             rp-json (aws/s3-get-object rp-key) :abort [(instance? js/Error rp-json) (do (u/put-close! out rp-json) out)]]
            (-> (.parse js/JSON rp-json)
                (js->clj :keywordize-keys true)))))
