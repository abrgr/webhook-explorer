(ns webhook-explorer.specs.handlers
  (:require [clojure.spec.alpha :as s]))

(s/def :handlers/match-type #{:exact :prefix})

(s/def :handlers/path string?)

(s/def :handlers/proto #{:https})

(s/def :handlers.handler/type #{:proxy :mock})

(s/def :handlers.handler.proxy/remote-url string?)

(s/def :handlers.handler/proxy
  (s/keys
    :req-un [:handlers.handler.proxy/remote-url]))

(s/def :handlers.handler.mock.res/status
  (s/int-in 100 600))

(s/def :handlers.handler.mock.res/headers
  (s/map-of string? string?))

(s/def :handlers.handler.mock.res/body
  string?)

(s/def :handlers.handler.mock/res
  (s/keys
    :req-un [:handlers.handler.mock.res/status
             :handlers.handler.mock.res/headers
             :handlers.handler.mock.res/body]))

(s/def :handlers.handler/mock
  (s/keys
    :req-un [:handlers.handler.mock/res]))

(s/def :handlers/handler
  (s/keys
    :req-un [:handlers.handler/type
             (or :handlers.handler/proxy
                 :handlers.handler/mock)]))

(s/def :handlers/matches
  (s/map-of
    string?
    string?))

(s/def :handlers/matcher
  (s/keys
    :req-un [:handlers/handler
             :handlers/matches]))

(s/def :handlers.captures/template-var string?)

(s/def :handlers.captures/template-var-spec
  (s/keys
    :req-un [:handlers.captures/template-var]))

(s/def :handlers.captures/headers
  (s/map-of
    string?
    :handlers.captures/template-var-spec))

(s/def :handlers.captures.body/type #{:json :form-data})

(s/def :handlers.captures.body/captures
  (s/map-of
    keyword?
    :handlers.captures/template-var-spec))

(s/def :handlers.captures/body
  (s/keys
    :req-un [:handlers.captures.body/type
             :handlers.captures.body/captures]))

(s/def :handlers/captures
  (s/keys
    :req-un [:handlers.captures/headers
             :handlers.captures/body]))

(s/def :handlers/matchers
  (s/coll-of :handlers/matcher :kind vector? :min-count 1))

(s/def :handlers/config
  (s/keys
    :req-un [:handlers/match-type
             :handlers/path
             :handlers/proto
             :handlers/matchers
             :handlers/captures]))
