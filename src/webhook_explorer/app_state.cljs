(ns webhook-explorer.app-state
  (:require [reagent.core :as r]
            [clojure.string :as s]))

(defonce nav (r/atom {:page :home
                      :params {}}))

(defonce auth (r/atom {:user-data nil :cognito-session nil}))

(def sample-items [{:date "2019-10-24"
                    :path "/abc"
                    :method "GET"
                    :id "yoyo"
                    :req-headers {
                      "Accept" "*/*"
                      "Host" "api.easybetes.com"
                      "User-Agent" "curl/7.47.0"}
                    :req-body "{\"a\": \"b\"}"
                    :res-headers {
                      "Content-Type" "application/edn"
                      "Host" "api.easybetes.com"
                      "User-Agent" "curl/7.47.0"}
                    :res-body "{:abc 7}"}
                   {:date "2019-10-23"
                    :path "/abc"
                    :method "POST"
                    :id "yoyo2"
                    :req-headers {
                      "Accept" "*/*"
                      "Content-Type" "application/json"
                      "Host" "api.easybetes.com"
                      "User-Agent" "curl/7.47.0"}
                    :req-body "{\"a\": \"b\"}"
                    :res-headers {
                      "Content-Type" "application/json"
                      "Host" "api.easybetes.com"
                      "User-Agent" "curl/7.47.0"}
                    :res-body "{\"a\": \"b\"}"}])

(defonce reqs (r/atom {:items []
                       :in-progress-req nil
                       :next-req {:folder "all"}
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
