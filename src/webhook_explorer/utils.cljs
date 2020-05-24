(ns webhook-explorer.utils
  (:require-macros [webhook-explorer.utils :refer [let+]])
  (:require [debux.cs.core :as d :refer-macros  [dbg dbgn]]
            [clojure.core.async :as async]))

(defn put-close! [c v]
  (async/put! c v #(async/close! c)))

(defn async-xform [xf in-ch]
  (let [out-ch (async/chan 1 xf)]
    (async/pipe in-ch out-ch)
    out-ch))

(defn async-do [doer in-ch]
  (async/take! in-ch doer))
