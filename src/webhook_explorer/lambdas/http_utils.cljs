(ns webhook-explorer.lambdas.http-utils
  (:require [clojure.core.async :as async]
            [clojure.string :as string]
            [goog.object :as obj]
            [cljs-http.client :as http-client]
            [cljs-http.util :as http-util]
            ["http" :as http]
            ["https" :as https]
            [webhook-explorer.utils :as u]))

(def default-port
  {:http 80
   :https 443})

(def default-runner
  {:http http/request
   :https https/request})

(defn node-req
  [{:keys [request-method scheme server-name server-port uri query-string headers body timeout cancel progress] :as request}]
  (let [c (async/chan)
        scheme (or scheme :http)
        runner (get default-runner scheme)
        opts {:hostname server-name
              :port (or server-port (get default-port scheme))
              :path (if (empty? query-string)
                          uri
                          (str uri "?" query-string))
              :method (name (or request-method :get))
              :headers (http-util/build-headers headers)}
        resp-body (atom (js/Buffer.alloc 0))
        aborted? (atom false)
        on-res (fn on-res [res]
                 (.on
                   res
                   "data"
                   (fn [data]
                     (swap! resp-body #(.concat % data))))
                 (.on
                   res
                   "end"
                   (fn []
                     (let [response  {:status (.statusCode res)
                                      :success ((every-pred pos-int? (partial > 400)) (.statusCode res))
                                      :body (.toString @resp-body "utf8") ; TODO: check content-type
                                      :headers (->> (.headers res)
                                                    js->clj
                                                    (into
                                                      {}
                                                      (map (fn [[k v]] [(string/lower-case k) v]))))}]
                       (if-not @aborted?
                         (async/put! c response))
                       (if cancel
                         (async/close! cancel))
                       (async/close! c)))))
        req (runner opts on-res)]
    (when timeout
      (.setTimeout req timeout))
    (when cancel
      (async/take! cancel #(.abort req)))
    (when progress
      (println "Progress not implemented"))
    (.on req "error" (fn [err] (u/put-close! c {:error-text (or (obj/get err "code") (obj/get err "message"))})))
    (.on req "timeout" (fn [] (u/put-close! c {:error-code :timeout :error-text "Timeout"})))
    (when body
      (.write req body))
    (.end req)
    c))

(def request (http-client/wrap-request node-req))
