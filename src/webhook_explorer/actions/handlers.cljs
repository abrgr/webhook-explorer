(ns webhook-explorer.actions.handlers
  (:require [clojure.spec.alpha :as s]
            [webhook-explorer.specs.handlers]
            [webhook-explorer.app-state :as app-state]
            [webhook-explorer.routes :as routes]))

(defn publish-handler [handler-config]
  (println handler-config))

(s/fdef publish-handler
  :args (s/cat :handler-config :handlers/config))
