(ns webhook-explorer.utils
  (:require [clojure.core.async :as async])
  (:require-macros [webhook-explorer.utils :refer [let+]]))

(defn put-close! [c v]
  (async/put! c v #(async/close! c))) 
