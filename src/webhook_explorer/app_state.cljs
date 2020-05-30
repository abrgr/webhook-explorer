(ns webhook-explorer.app-state
  (:require [reagent.core :as r]
            [clojure.string :as s]
            [webhook-explorer.state-machines.handlers :as handler-machine]
            [webhook-explorer.state-machines.packages :as packages-machine]
            [webhook-explorer.state-machines.package-executions :as package-executions-machine]
            [webhook-explorer.state-machines.edit-package :as edit-package-machine]))

(defonce nav (r/atom {:page :home
                      :params {}}))

(defonce auth (r/atom {:user-data nil :cognito-session nil}))

(defonce tags (r/atom {:user [] :public {:readable [] :writable []}}))

(defonce reqs (r/atom {:items []
                       :selected-item nil
                       :next-req {}
                       :tagged-reqs {}
                       :earliest-tagged-req nil
                       :next-tagged-req {}}))

(defonce users (r/atom {:users []
                        :next-req {}
                        :error nil}))

(defonce handlers (r/atom {:handlers []
                           :next-req {:proto "https" :method "get"}
                           :error nil}))

(defonce handler (handler-machine/svc))

(defonce edit-package (edit-package-machine/svc))

(defonce packages (packages-machine/svc))

(defonce package-executions (package-executions-machine/svc))

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
