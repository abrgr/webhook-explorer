(ns webhook-explorer.promise-utils
  (:require [clojure.core.async :as async]))

(defn chan->promise [ch]
  (js/Promise.
   (fn [res rej]
     (async/take! ch #(if (instance? js/Error %) (rej %) (res %))))))
