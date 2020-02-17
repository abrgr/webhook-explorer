(ns webhook-explorer.env
  (:require [goog.object :as obj]))

(def ^:private -env
  (->> "config"
       (js/document.getElementById)
       (.-innerHTML)
       (.parse js/JSON)))

(def cognito-cfg (obj/get -env "cognito"))

(def version (obj/get -env "version"))

(def rogo-domain (obj/get -env "rogoDomain"))

(def api-base (obj/get -env "rogoApiUrl"))

(def handler-domains (->> "handlerDomains" (obj/get -env) js->clj))
