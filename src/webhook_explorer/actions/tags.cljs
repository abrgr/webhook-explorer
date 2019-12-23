(ns webhook-explorer.actions.tags
  (:require [webhook-explorer.app-state :as app-state]
            [webhook-explorer.init :as init]
            [webhook-explorer.http-utils :as http-utils]
            [clojure.core.async :as async]
            [cljs-http.client :as http]))

(defn load-tags []
  (async/go
    (let [res (async/<! (http/get
                          (http-utils/make-url "/api/tags")
                          {:with-credentials? false
                           :headers (http-utils/auth-headers)}))
          {{:keys [userTags]
            {:keys [readable writable]} :publicTags} :body} res]
      (println res)
      (reset! app-state/tags {:user userTags
                              :public {:readable readable
                                       :writable writable}}))))

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
