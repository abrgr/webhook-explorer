(ns webhook-explorer.app-state
  (:require [reagent.core :as r]
            [clojure.string :as s]))

(defonce nav (r/atom {:page :home
                      :params {}}))

(defonce auth (r/atom {:user-data nil :cognito-session nil}))

(defonce reqs (r/atom {:items [{:date "2019-10-24"
                                :path "/abc"
                                :method "GET"
                                :id "yoyo"
                                :headers {
                                  "Accept" "*/*"
                                  "Host" "api.easybetes.com"
                                  "User-Agent" "curl/7.47.0"}
                                :body "{\"a\": \"b\"}"}
                               {:date "2019-10-23"
                                :path "/abc"
                                :method "POST"
                                :id "yoyo2"
                                :headers {
                                  "Accept" "*/*"
                                  "Host" "api.easybetes.com"
                                  "User-Agent" "curl/7.47.0"}
                                :body "{\"a\": \"b\"}"}]
                       :in-progress-req nil
                       :favorite-reqs #{"yoyo2"}}))

(defn logged-in? []
  (some? (:cognito-session @auth)))

(defn- split-name [n]
  (let [parts (s/split n #"\W")]
    {:given-name (first parts)
     :family-name (last parts)}))

(defn user-name [{:keys [family_name given_name name nickname email]}]
  (let [{:keys [given-name family-name]}
        (cond
          (and (some? family_name) (some? given_name)) {:family-name family_name :given-name given_name}
          (some? name) (split-name name)
          (some? nickname) (split-name nickname)
          (some? email) (split-name (s/replace email #"@.*"  "")))]
    {:given-name given-name
     :family-name family-name
     :name (str given-name " " family-name)}))

(defn user-info []
  (let [user-data (:user-data @auth)]
    (merge
      (user-name user-data)
      {:pic-url (:picture user-data)
       :email (:email user-data)})))
