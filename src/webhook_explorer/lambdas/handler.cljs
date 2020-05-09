(ns webhook-explorer.lambdas.handler
  (:require [clojure.spec.alpha :as s]
            [webhook-explorer.specs.aws]
            [webhook-explorer.specs.chan :as c]))

(defmulti handler (fn [event context] (:resource event)))

(s/fdef handler
  :args (s/cat :event :aws/event.apigw-proxy
               :context :aws/context)
  :ret c/chan?)
