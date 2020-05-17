(ns webhook-explorer.specs.aws
  (:require [clojure.spec.alpha :as s]))

(s/def :aws.event.apigw-proxy.request-context.authorizer/claims
  (s/map-of string? string?))

(s/def :aws.event.apigw-proxy.request-context/authorizer
  (s/keys :req-un [:aws.event.apigw-proxy.request-context.authorizer/claims]))
(s/def :aws.event.apigw-proxy.request-context/account-id string?)
(s/def :aws.event.apigw-proxy.request-context/request-id string?)
(s/def :aws.event.apigw-proxy.request-context/request-time-epoch pos-int?)
(s/def :aws.event.apigw-proxy.request-context/stage string?)
(s/def :aws.event.apigw-proxy.request-context/path string?)
(s/def :aws.event.apigw-proxy.request-context/resource-path string?)
(s/def :aws.event.apigw-proxy.request-context/http-method string?)
(s/def :aws.event.apigw-proxy.request-context/api-id string?)
(s/def :aws.event.apigw-proxy.request-context/protocol string?)

(s/def :aws.event.apigw-proxy/body string?)
(s/def :aws.event.apigw-proxy/resource string?)
(s/def :aws.event.apigw-proxy/path string?)
(s/def :aws.event.apigw-proxy/http-method #{"GET" "POST" "PUT" "PATCH" "DELETE" "OPTIONS"})
(s/def :aws.event.apigw-proxy/is-base64-encoded boolean?)
(s/def :aws.event.apigw-proxy/query-string-parameters (s/map-of string? string?))
(s/def :aws.event.apigw-proxy/multi-value-query-string-parameters (s/map-of string? (s/coll-of string?)))
(s/def :aws.event.apigw-proxy/path-parameters (s/map-of string? string?))
(s/def :aws.event.apigw-proxy/stage-variables (s/map-of string? string?))
(s/def :aws.event.apigw-proxy/headers (s/map-of string? string?))
(s/def :aws.event.apigw-proxy/multi-value-headers (s/map-of string? (s/coll-of string?)))
(s/def :aws.event.apigw-proxy/request-context
  (s/keys :req-un [:aws.event.apigw-proxy.request-context/account-id
                   :aws.event.apigw-proxy.request-context/request-id
                   :aws.event.apigw-proxy.request-context/request-time-epoch
                   :aws.event.apigw-proxy.request-context/stage
                   :aws.event.apigw-proxy.request-context/path
                   :aws.event.apigw-proxy.request-context/resource-path
                   :aws.event.apigw-proxy.request-context/http-method
                   :aws.event.apigw-proxy.request-context/api-id
                   :aws.event.apigw-proxy.request-context/protocol
                   :aws.event.apigw-proxy.request-context/authorizer]))

(s/def :aws/event.apigw-proxy
  (s/keys :req-un [:aws.event.apigw-proxy/body
                   :aws.event.apigw-proxy/resource
                   :aws.event.apigw-proxy/path
                   :aws.event.apigw-proxy/http-method
                   :aws.event.apigw-proxy/is-base64-encoded
                   :aws.event.apigw-proxy/query-string-parameters
                   :aws.event.apigw-proxy/multi-value-query-string-parameters
                   :aws.event.apigw-proxy/path-parameters
                   :aws.event.apigw-proxy/stage-variables
                   :aws.event.apigw-proxy/headers
                   :aws.event.apigw-proxy/multi-value-headers
                   :aws.event.apigw-proxy/request-context]))

(s/def :aws/event
  (s/or :apigw-proxy :aws/event.apigw-proxy))

(s/def :aws.context/function-name string?)
(s/def :aws.context/function-version string?)
(s/def :aws.context/invoked-function-arn string?)
(s/def :aws.context/memory-limit-in-mb pos-int?)
(s/def :aws.context/aws-request-id string?)
(s/def :aws.context/log-group-name string?)
(s/def :aws.context/log-stream-name string?)

(s/def :aws/context
  (s/keys :req-un [:aws.context/function-name
                   :aws.context/function-version
                   :aws.context/invoked-function-arn
                   :aws.context/memory-limit-in-mb
                   :aws.context/aws-request-id
                   :aws.context/log-group-name
                   :aws.context/log-stream-name]))
