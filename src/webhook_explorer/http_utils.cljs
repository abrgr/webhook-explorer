(ns webhook-explorer.http-utils
  (:require [clojure.core.async :as async]
            [webhook-explorer.actions.auth :as auth-actions]
            [webhook-explorer.env :as env]
            [camel-snake-kebab.core :as csk]
            [camel-snake-kebab.extras :as cske]
            [cljs-http.client :as http]))

(defn make-api-url [path]
  (str env/api-base path))

(defn auth-headers []
  {"Authorization" (auth-actions/auth-header)})

(defn req [{:keys [json-params path literal-res-paths literal-req-paths] :as opts}]
  (async/go
    (let [opts' (cond-> opts
                  (contains? opts :json-params) (assoc :json-params (cske/transform-keys csk/->camelCase json-params))
                  true (dissoc :path)
                  true (dissoc :literal-res-paths)
                  true (assoc :url (make-api-url path))
                  true (update :headers merge (auth-headers))
                  true (assoc :with-credentials? false))
          opts' (reduce
                  (fn [opts' lit-req-path]
                    (assoc-in opts' (concat [:json-params] (map csk/->camelCase lit-req-path)) (get-in json-params lit-req-path)))
                  opts'
                  literal-req-paths)
          res (async/<! (http/request opts'))
          {:keys [status headers body]} res
          body' (reduce
                  (fn [body' lit-resp-path]
                    (assoc-in body' (map csk/->kebab-case-keyword lit-resp-path) (get-in body lit-resp-path)))
                  (cske/transform-keys csk/->kebab-case-keyword body)
                  literal-res-paths)]
      {:body body'
       :status status
       :headers headers})))
