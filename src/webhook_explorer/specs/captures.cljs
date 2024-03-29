(ns webhook-explorer.specs.captures
  (:require [clojure.spec.alpha :as s]))

(s/def :captures/template-var string?)
(s/def :captures/template-var-spec
  (s/keys
   :req-un [:captures/template-var]))
(s/def :captures.body/type #{:json :form-data})
(s/def :captures.body/captures
  (s/map-of
   string?
   :captures/template-var-spec))
(s/def :captures/body
  (s/keys
   :req-un [:captures.body/type
            :captures.body/captures]))
(s/def :captures/headers
  (s/map-of
   string?
   :captures/template-var-spec))
(s/def :captures/cookies
  (s/map-of
   string?
   :captures/template-var-spec))
(s/def :captures/status
  :captures/template-var-spec)
(s/def :captures/captures
  (s/keys
   :opt-un [:captures/headers
            :captures/cookies
            :captures/body
            :captures/status]))
