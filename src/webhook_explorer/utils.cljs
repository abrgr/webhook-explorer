(ns webhook-explorer.utils
  (:require-macros [webhook-explorer.utils :refer [let+]])
  (:require [debux.cs.core :as d :refer-macros  [dbg dbgn]]
            [clojure.core.async :as async]
            [clojure.string :as string]
            [camel-snake-kebab.core :as csk]
            [camel-snake-kebab.extras :as cske]))

(defn put-close! [c v]
  "Put v to port c and close port c."
  (async/put! c v #(async/close! c)))

(defn pass-errors [f]
  "Return a new function of 1 argument, x. If x is an Error, return it, otherwise, return (f x)."
  (fn error-passer [x]
    (if (instance? js/Error x)
      x
      (f x))))

(defn spy-chan [n in-ch]
  "Return a new channel that receives all items from in-ch. Items are printed with 'n' prefix as they are read."
  (let [out (async/chan)]
    (async/go-loop [x (async/<! in-ch)]
     (when x
       (println n x)
       (async/>! out x)
       (recur (async/<! in-ch)))
     (println n "CLOSING")
     (async/close! out))
    out))

(defn async-xform [xf in-ch]
  "Return a new channel that will receive values from in-ch transformed by the transducer xf."
  (let [out-ch (async/chan 1 xf)]
    (async/pipe in-ch out-ch)
    out-ch))

(defn async-xform-all [xf in-ch]
  "Return a new channel that will receive the result of applying transducer xf to the vector of all items in in-ch."
  (async-xform xf (async/into [] in-ch)))

(defn async-do [doer in-ch]
  "Apply doer to one item in in-ch."
  (async/take! in-ch doer))

(defn async-unwrap [in-ch]
  (let [out-ch (async/chan)]
    (async/go
      (-> in-ch
          (->> (async/into []))
          async/<!
          async/merge
          (async/pipe out-ch)))
    out-ch))

(defn pad-start [s n pad-char]
  "Left-pad string s with pad-char to length s"
  (let [padding (max 0 (- n (count s)))]
    (-> (repeat padding pad-char)
        vec
        (conj s)
        string/join)))

(def descending-s3-date-parts
  [{:fun (memfn getFullYear) :max 10000 :padding 4}
   {:const "-"}
   {:fun (memfn getMonth) :max 13 :padding 2 :xform inc}
   {:const "-"}
   {:fun (memfn getDate) :max 32 :padding 2}
   {:const "T"}
   {:fun (memfn getHours) :max 25 :padding 2}
   {:const ":"}
   {:fun (memfn getMinutes) :max 61 :padding 2}
   {:const ":"}
   {:fun (memfn getSeconds) :max 61 :padding 2}
   {:const "."}
   {:fun (memfn getMilliseconds) :max 1000 :padding 3}
   {:const "|"}
   {:str-fun (memfn toISOString)}])

(defn descending-s3-date
  "Returns a string that will sort in descending order by date followed by | followed by an iso string"
  ([]
   (descending-s3-date (js/Date.)))
  ([d]
   (->> descending-s3-date-parts
        (map
         (fn [{:keys [fun str-fun max padding xform const]
               :or {xform identity}}]
           (cond
             const const
             str-fun (str-fun d)
             :else (-> d
                       fun
                       xform
                       (->> (- max))
                       (#(if padding
                           (pad-start (str %) padding "0")
                           %))))))
        (string/join))))

(defn js->kebab-clj [js]
  (-> js
      (js->clj :keywordize-keys true)
      (->> (cske/transform-keys csk/->kebab-case-keyword))))

(defn clj->camel-js [c]
  (-> c
      (->> (cske/transform-keys csk/->camelCaseKeyword))
      clj->js))

(defn json->kebab-clj [j]
  (try
    (some-> j (js/JSON.parse) js->kebab-clj)
    (catch js/Error e
      (.error js/console {:msg "Failed to parse json"
                          :json j
                          :error e})
      (throw e))))

(defn clj->camel-json [c]
  (some-> c
          clj->camel-js
          (js/JSON.stringify)))
