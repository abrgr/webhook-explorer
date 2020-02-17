(ns webhook-explorer.actions.auth
  (:require [webhook-explorer.app-state :as app-state]
            [webhook-explorer.routes :as routes]
            [webhook-explorer.init :as init]
            [webhook-explorer.env :as env]
            ["amazon-cognito-auth-js" :as cognito-auth]))

(defn- make-cognito-auth []
  (->> env/cognito-cfg
       (cognito-auth/CognitoAuth.)))

(declare current-user-data)

(defn- with-user-handler [c]
  (set!
    (.-userhandler c)
    #js {:onSuccess #(do (reset! app-state/auth {:user-data (current-user-data %) :cognito-session %})
                         (routes/nav-to-reqs))
         :onFailure #(routes/nav-to-auth {:query-params {:failure true}})})
  c)

(def ^:private ca (->> (make-cognito-auth) with-user-handler))

(defn- unexpired? []
  (-> ca
      (.getSignInUserSession)
      (.getIdToken)
      (.getExpiration)
      (* 1000)
      (> (js/Date.now))))

(defn- logged-in? []
  (and
    (.isUserSignedIn ca)
    (unexpired?)))

(defn- current-user-data
  ([]
    (current-user-data (.getSignInUserSession ca)))
  ([sess]
    (-> sess
        (.getIdToken)
        (.decodePayload)
        (js->clj :keywordize-keys true))))

(init/register-init
  0
  (fn []
    (when (logged-in?)
      (reset! app-state/auth {:user-data (current-user-data) :cognito-session (.getSignInUserSession ca)}))))

(init/register-init
  2
  (fn []
    (.parseCognitoWebResponse ca js/window.location.href)))

(defn sign-in []
  (.getSession ca))

(defn auth-header []
  (-> ca
      (.getSignInUserSession)
      (.getIdToken)
      (.getJwtToken)))

(defn sign-out []
  (.signOut ca))
