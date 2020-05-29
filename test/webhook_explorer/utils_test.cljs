(ns webhook-explorer.utils-test
  (:require [webhook-explorer.utils :as u]
            [cljs.spec.test.alpha :as stest]
            [cljs.test :refer-macros [deftest testing is] :as tst]))

(stest/instrument)

(deftest descending-s3-date-test
  (let [d (doto (js/Date.)
            (.setFullYear 2020)
            (.setMonth 10)
            (.setDate 4)
            (.setHours 9)
            (.setMinutes 25)
            (.setSeconds 59)
            (.setMilliseconds 100))
        res (u/descending-s3-date d)]
    (is (= res "7980-02-28T16:36:02.900|2020-11-04T14:25:59.100Z"))))
