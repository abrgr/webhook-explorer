(ns webhook-explorer.actions.handlers
  (:require [clojure.core.async :as async]
            [clojure.spec.alpha :as s]
            [cljs-http.client :as http]
            [webhook-explorer.http-utils :as http-utils]
            [webhook-explorer.specs.handlers]
            [webhook-explorer.app-state :as app-state]
            [webhook-explorer.routes :as routes]))

(defn publish-handler [handler-config]
  (async/go
    (let [res (async/<! (http/post
                          (http-utils/make-url "/api/handlers")
                          {:with-credentials? false
                           :headers (http-utils/auth-headers)
                           :json-params {:handler (assoc handler-config :domain "api.easybetes.com")}}))
          {{:keys [success]} :body} res]
      (boolean success))))

(s/fdef publish-handler
  :args (s/cat :handler-config :handlers/config))
