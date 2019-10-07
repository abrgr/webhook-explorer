(ns webhook-explorer.containers.auth
  (:require [reagent.core :as r]
            [secretary.core :as secretary]
            [webhook-explorer.app-state :as app-state]
            [webhook-explorer.routes :as routes]
            ["amazon-cognito-auth-js" :as cognito-auth]))

(defn- make-cognito-auth []
  (->> "cognito-config"
       (js/document.getElementById)
       (.-innerHTML)
       (.parse js/JSON)
       (cognito-auth/CognitoAuth.)))

(defn- with-user-handler [c]
  (set!
    (.-userhandler c)
    #js {:onSuccess #(do (reset! app-state/auth {:cognito-auth %})
                         (routes/nav-to-home))
         :onFailure #(routes/nav-to-auth {:query-params {:failure true}})})
  c)

(defn- sign-in [ca]
  (.getSession ca))

(defn component []
  (let [ca (->> (make-cognito-auth) with-user-handler)]
    (.parseCognitoWebResponse ca js/window.location.href)
    [:div
      [:input {:type "button"
               :value "Sign in"
               :on-click (partial sign-in ca)}]]))
