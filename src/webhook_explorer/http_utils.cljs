(ns webhook-explorer.http-utils
  (:require [webhook-explorer.actions.auth :as auth-actions]))

(defn make-url [path]
  (str "https://webhook-explorer.easybetes.com" path))

(defn auth-headers []
  {"Authorization" (auth-actions/auth-header)})
