(ns webhook-explorer.actions.tags
  (:require [webhook-explorer.app-state :as app-state]
            [webhook-explorer.init :as init]
            [webhook-explorer.http-utils :as http-utils]
            [clojure.core.async :as async]
            [cljs-http.client :as http]))

(defn load-tags []
  (async/go
    (let [res (async/<! (http-utils/req
                         {:method :get
                          :path "tags"}))
          {{:keys [user-tags]
            {:keys [readable writable]} :public-tags} :body} res]
      (reset! app-state/tags {:user (set user-tags)
                              :public {:readable (set readable)
                                       :writable (set writable)}}))))

(defn add-tag [{:keys [pub tag]}]
  (when tag
    (if pub
      (swap!
       app-state/tags
       update
       :public
       #(-> %
            (update :readable conj tag)
            (update :writable conj tag)))
      (swap!
       app-state/tags
       update
       :user
       conj
       tag))))

(init/register-init
 10
 (fn []
   (if (app-state/logged-in?)
     (load-tags)
     (add-watch
      app-state/auth
      ::load-tags
      (fn [_ _ _ _]
        (when (app-state/logged-in?)
          (remove-watch app-state/auth ::load-tags)
          (load-tags)))))))
