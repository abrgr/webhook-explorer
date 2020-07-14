(ns webhook-explorer.remote.s3-load
  (:require [clojure.string :as string]
            [goog.object :as obj]
            [goog.Uri :as uri]
            [goog.uri.utils :as uu]
            [cljs-http.client :as http]
            [cljs-http.core :as http-core]))

; regular cljs-http breaks s3 presigned requests with url encoded path components because goog.Uri decodes path components because...

; slightly modified from https://github.com/r0man/cljs-http/blob/3bde7ab4f6ee320486dd6801d6d034b6ad360ba2/src/cljs_http/client.cljs#L33
(defn parse-url-no-munge
  "Parse `url` into a hash map."
  [url]
  (if-not (string/blank? url)
    (let [uri (uri/parse url)
          query-data (.getQueryData uri)
          url-parts (uu/split url)
          path (aget url-parts (obj/get uu/ComponentIndex "PATH"))]
      {:scheme (keyword (.getScheme uri))
       :server-name (.getDomain uri)
       :server-port (http/if-pos (.getPort uri))
       :uri path ; we don't take the decoded path from goog's uri because it breaks s3 presigned urls with url encoded path elements
       :query-string (if-not (.isEmpty query-data)
                       (str query-data))
       :query-params (if-not (.isEmpty query-data)
                       (http/parse-query-params (str query-data)))})))

; from https://github.com/r0man/cljs-http/blob/3bde7ab4f6ee320486dd6801d6d034b6ad360ba2/src/cljs_http/client.cljs#L235
(defn wrap-url-no-munge [client]
  (fn [{:keys [query-params] :as req}]
    (if-let [spec (parse-url-no-munge (:url req))]
      (client (-> (merge req spec)
                  (dissoc :url)
                  (update-in [:query-params] #(merge %1 query-params))))
      (client req))))

; from https://github.com/r0man/cljs-http/blob/3bde7ab4f6ee320486dd6801d6d034b6ad360ba2/src/cljs_http/client.cljs#L235
(def request
  (-> http-core/request
      http/wrap-accept
      http/wrap-form-params
      http/wrap-multipart-params
      http/wrap-edn-params
      http/wrap-edn-response
      http/wrap-transit-params
      http/wrap-transit-response
      http/wrap-json-params
      http/wrap-json-response
      http/wrap-content-type
      http/wrap-query-params
      http/wrap-basic-auth
      http/wrap-oauth
      http/wrap-method
      ; http/wrap-url - this breaks s3 presigned requests with url encoded path components because goog.Uri decodes path components because...
      wrap-url-no-munge
      http/wrap-channel-from-request-map
      http/wrap-default-headers))
