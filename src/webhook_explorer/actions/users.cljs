(ns webhook-explorer.actions.users
  (:require [clojure.core.async :as async]
            [cljs-http.client :as http]
            [webhook-explorer.app-state :as app-state]
            [webhook-explorer.http-utils :as http-utils]
            [webhook-explorer.promise-utils :as putil]))

(defn- get-users [params]
  (if (nil? params)
    (do (swap! app-state/users assoc :next-req nil)
        (async/to-chan [:stop]))
    (async/go
      (let [{{:keys [users nextReq]} :body} (async/<! (http-utils/req
                                                        {:method :get
                                                         :path "users"
                                                         :query-params params}))]
        (swap!
          app-state/users
          (fn [{prev-users :users :as prev}]
            (merge
              prev
              {:users (->> users
                           (concat prev-users)
                           (into []))
               :next-req nextReq})))
        :done))))

(def ^:private req-chan (async/chan))

(async/go-loop []
  (let [resp-chan (async/<! req-chan)
        {:keys [next-req]} @app-state/users
        resp (async/<! (get-users next-req))]
    (async/>! resp-chan resp)
    (recur)))

(defn load-next-users []
  (let [resp-chan (async/chan)
        p (putil/chan->promise resp-chan)]
    (async/put! req-chan resp-chan)
    p))

(defn create-user [{:keys [email role]}]
  (async/go
    (let [res (async/<! (http/post
                          (http-utils/make-api-url "users")
                          {:with-credentials? false
                           :headers (http-utils/auth-headers)
                           :json-params {:user {:email email
                                                :role role}}}))
          {{:keys [user]
            {err-msg :msg} :error} :body} res]
      (if user
        (swap!
          app-state/users
          update
          :users
          conj
          user)
        (swap!
          app-state/users
          assoc
          :error
          (or err-msg "Failed to create user")))
      (some? user))))

(defn update-user [{:keys [username]} k v]
  (async/go
    (let [res (async/<! (http/post
                          (http-utils/make-api-url (str "users/" username))
                          {:with-credentials? false
                           :headers (http-utils/auth-headers)
                           :json-params {:actions [{k v}]}}))
          {:keys [status]} res
          success (= status 200)]
      (when success
        (swap!
          app-state/users
          update
          :users
          (fn [prev-users]
            (mapv
              #(if (= (:username %) username)
                (assoc % k v)
                %)
              prev-users))))
      success)))
