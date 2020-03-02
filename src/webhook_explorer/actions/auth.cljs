(ns webhook-explorer.actions.auth
  (:require [webhook-explorer.app-state :as app-state]
            [webhook-explorer.routes :as routes]
            [webhook-explorer.init :as init]
            [webhook-explorer.auth :as core-auth]
            ["amazon-cognito-auth-js" :as cognito-auth]))

(reset! core-auth/on-login-success
  (fn [sess]
    (reset!
      app-state/auth
      {:user-data (core-auth/current-user-data sess)
       :cognito-session sess})
    (routes/nav-to-reqs)))

(reset! core-auth/on-login-failure
  (fn [] (routes/nav-to-auth {:query-params {:failure true}})))

(init/register-init
  0
  (fn []
    (when (core-auth/logged-in?)
      (reset! app-state/auth {:user-data (core-auth/current-user-data)
                              :cognito-session (core-auth/signed-in-user-session)}))))

(init/register-init
  2
  (fn []
    (core-auth/parse-web-response)))

(defn sign-in []
  (core-auth/sign-in))

(defn auth-header []
  (core-auth/auth-header))

(defn sign-out []
  (core-auth/sign-out))
