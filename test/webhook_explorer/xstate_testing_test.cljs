(ns webhook-explorer.xstate-testing-test
  (:require [clojure.spec.test.alpha :as stest]
            [clojure.spec.alpha :as s]
            [clojure.core.async :as async]
            [webhook-explorer.xstate :as xs]
            [webhook-explorer.xstate-testing :as xst]
            [cljs.test :refer-macros [deftest testing is] :as tst]))

(stest/instrument)

(s/def ::b
  #{:b})
(s/def ::b-ctx
  (s/keys
   :req-un [::b]))

(defn test-machine [cfg expect-success done]
   (let [machine (xs/machine {:cfg (xs/cfg->machine :test-machine cfg)})
         model (xst/model {:machine machine
                           :actions {:b-act (xs/assign-ctx {:ctx-prop :b
                                                            :static-ctx :b})}
                           :ctx {}
                           :ctx-specs {:b (s/get-spec ::b-ctx)}})]
     (async/go
       (let [failed (async/<! (async/into [] (xst/test-simple-paths model)))]
         (is ((if expect-success = >) (count failed) 0))
         (is (xst/is-covered model))
         (done)))))

(deftest machine-spec-test
  (testing "machine-spec-passing"
    (tst/async
     done
     (test-machine
       '[* [[:do-b -> :b ! :b-act]]
         > :a [[:b -> :b ! :b-act]]
         :b []]
       true
       done)))
  (testing "machine-spec-failing"
    (tst/async
     done
     (test-machine
       '[* [[:do-b -> :b ! :b-act]]
         > :a [[:b -> :b]]
         :b []]
       false
       done))))
