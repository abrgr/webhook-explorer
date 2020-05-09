(ns webhook-explorer.specs.chan
  (:require [clojure.spec.alpha :as s]
            [clojure.core.async.impl.protocols :as async-protos]))

(defn chan? [c]
  "Tests if c is a core.async.chan"
  (satisfies? async-protos/WritePort c))
