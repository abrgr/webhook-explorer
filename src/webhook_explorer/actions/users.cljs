(ns webhook-explorer.actions.users
  (:require [clojure.core.async :as async]
            [cljs-http.client :as http]
            [webhook-explorer.app-state :as app-state]
            [webhook-explorer.http-utils :as http-utils]
            [webhook-explorer.promise-utils :as putil]))

(defn- get-users [params]
  (if (nil? params)
    (async/to-chan [:stop])
    (async/go
      (let [{{:keys [users nextReq]} :body} (async/<! (http/get
                                                        (http-utils/make-url "/api/users")
                                                        {:with-credentials? false
                                                         :headers (http-utils/auth-headers)
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
                          (http-utils/make-url "/api/users")
                          {:with-credentials? false
                           :headers (http-utils/auth-headers)
                           :json-params {:user {:email email
                                                :role role}}}))
          {{:keys [user status]
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
