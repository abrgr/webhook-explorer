(ns webhook-explorer.utils
  (:require-macros [webhook-explorer.utils :refer [let+]]) 
  (:require [clojure.core.async :as async]))

(defn put-close! [c v]
  (async/put! c v #(async/close! c)))
