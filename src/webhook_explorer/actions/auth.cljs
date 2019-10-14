(ns webhook-explorer.actions.auth
  (:require [webhook-explorer.app-state :as app-state]
            [webhook-explorer.routes :as routes]
            [webhook-explorer.init :as init]
            ["amazon-cognito-auth-js" :as cognito-auth]))

(defn- make-cognito-auth []
  (->> "cognito-config"
       (js/document.getElementById)
       (.-innerHTML)
       (.parse js/JSON)
       (cognito-auth/CognitoAuth.)))

(declare current-user-data)

(defn- with-user-handler [c]
  (set!
    (.-userhandler c)
    #js {:onSuccess #(do (reset! app-state/auth {:user-data (current-user-data) :cognito-session %})
                         (routes/nav-to-home))
         :onFailure #(routes/nav-to-auth {:query-params {:failure true}})})
  c)

(def ^:private ca (->> (make-cognito-auth) with-user-handler))

(defn- logged-in? []
  (.isUserSignedIn ca))

(defn- current-user-data []
  (-> ca
      (.getSignInUserSession)
      (.getIdToken)
      (.-payload)
      (js->clj :keywordize-keys true)))

(init/register-init
  0
  (fn []
    (reset! app-state/auth {:user-data (current-user-data) :cognito-session (.getSignInUserSession ca)})))

(init/register-init
  2
  (fn []
    (.parseCognitoWebResponse ca js/window.location.href)))

(defn sign-in []
  (.getSession ca))
