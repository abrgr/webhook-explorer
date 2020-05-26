(ns webhook-explorer.utils
  (:require-macros [webhook-explorer.utils :refer [let+]])
  (:require [debux.cs.core :as d :refer-macros  [dbg dbgn]]
            [clojure.core.async :as async]))

(defn put-close! [c v]
  (async/put! c v #(async/close! c)))

(defn pass-errors [f]
  (fn error-passer [x]
    (if (instance? js/Error x)
      x
      (f x))))

(defn async-xform [xf in-ch]
  (let [out-ch (async/chan 1 xf)]
    (async/pipe in-ch out-ch)
    out-ch))

(defn async-xform-all [xf in-ch]
  (async-xform xf (async/into [] in-ch)))

(defn async-do [doer in-ch]
  (async/take! in-ch doer))
