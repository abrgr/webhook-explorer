(ns webhook-explorer.http-utils
  (:require [clojure.core.async :as async]
            [webhook-explorer.auth :as core-auth]
            [webhook-explorer.env :as env]
            [camel-snake-kebab.core :as csk]
            [camel-snake-kebab.extras :as cske]
            [cljs-http.client :as http]))

(defn make-api-url [path]
  (str env/api-base path))

(defn auth-headers []
  {"Authorization" (core-auth/auth-header)})

(defn error-for-status [status]
  (cond
    (= status 0)   (js/Error. "Network issue")
    (= status 400) (js/Error. "Bad request")
    (= status 401) (js/Error. "Unauthorized")
    (= status 404) (js/Error. "Not found")
    (= status 409) (js/Error. "Conflict")
    (> status 399) (js/Error. "Error")
    :else nil))

(defn req [{:keys [json-params query-params path literal-res-paths literal-req-paths] :as opts}]
  (async/go
    (let [opts' (cond-> opts
                  (contains? opts :json-params) (assoc :json-params (cske/transform-keys csk/->camelCase json-params))
                  (contains? opts :query-params) (assoc :query-params (cske/transform-keys csk/->camelCase query-params))
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
       :error (error-for-status status)
       :status status
       :headers headers})))
