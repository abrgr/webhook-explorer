(ns webhook-explorer.auth
  (:require [webhook-explorer.env :as env]
            ["amazon-cognito-auth-js" :as cognito-auth]))

(defn- make-cognito-auth []
  (->> env/cognito-cfg
       (cognito-auth/CognitoAuth.)))

(def on-login-success (atom (fn [])))
(def on-login-failure (atom (fn [])))

(defn- with-user-handler [c]
  (set!
    (.-userhandler c)
    #js {:onSuccess #(@on-login-success %)
         :onFailure #(@on-login-failure)})
  c)

(def ^:private ca (->> (make-cognito-auth) with-user-handler))

(defn- unexpired? []
  (-> ca
      (.getSignInUserSession)
      (.getIdToken)
      (.getExpiration)
      (* 1000)
      (> (js/Date.now))))

(defn logged-in? []
  (and
    (.isUserSignedIn ca)
    (unexpired?)))

(defn current-user-data
  ([]
    (current-user-data (.getSignInUserSession ca)))
  ([sess]
    (-> sess
        (.getIdToken)
        (.decodePayload)
        (js->clj :keywordize-keys true))))

(defn sign-in []
  (.getSession ca))

(defn auth-header []
  (-> ca
      (.getSignInUserSession)
      (.getIdToken)
      (.getJwtToken)))

(defn sign-out []
  (.signOut ca))

(defn parse-web-response []
  (.parseCognitoWebResponse ca js/window.location.href))

(defn signed-in-user-session []
  (.getSignInUserSession ca))
