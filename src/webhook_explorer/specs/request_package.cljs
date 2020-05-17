(ns webhook-explorer.specs.request-package
  (:require [clojure.spec.alpha :as s]
            [webhook-explorer.specs.captures]))

(s/def :request-package/name string?)
(s/def :request-package/input-template-vars
  (s/coll-of string?))
(s/def :request-package.req/name string?)
(s/def :request-package.req/headers
  (s/map-of string? string?))
(s/def :request-package.req/qs
  (s/map-of string? string?))
(s/def :request-package.req/body string?)
(s/def :request-package.req/req
  (s/keys :req-un [:request-package.req/headers
                   :request-package.req/qs
                   :request-package.req/body]))
(s/def :request-package/req
  (s/keys :req-un [:request-package.req/name
                   :captures/captures
                   :request-package.req/req]))
(s/def :request-package/reqs
  (s/coll-of :request-package/req))
(s/def :request-package/package
  (s/keys :req-un [:request-package/name
                   :request-package/input-template-vars
                   :request-package/reqs]))
