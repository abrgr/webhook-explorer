(ns webhook-explorer.lambdas.env
  (:require [cljs.nodejs :as njs]
            [goog.object :as obj]))

(def bucket (obj/getValueByKeys njs/process #js ["env" "BUCKET_NAME"]))
(def execution-queue-url (obj/getValueByKeys njs/process #js ["env" "EXECUTION_QUEUE_URL"]))
