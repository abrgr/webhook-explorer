(ns webhook-explorer.xstate-testing-test
  (:require [clojure.spec.test.alpha :as stest]
            [clojure.spec.alpha :as s]
            [clojure.core.async :as async]
            [webhook-explorer.xstate :as xs]
            [webhook-explorer.xstate-testing :as xst]
            [cljs.test :refer-macros [deftest testing is] :as tst]))

(stest/instrument)

(deftest machine-spec-test
  (testing "machine-spec"
    (tst/async done
               (let [cfg '[* [[:do-b -> :b ! :b-act]]
                           > :a [[:b -> :b]]
                           :b []]
                     machine (xs/machine {:cfg (xs/cfg->machine :test-machine cfg)})
                     model (xst/model {:machine machine
                                       :actions {:b-act (xs/assign-ctx {:ctx-prop :b
                                                                        :static-ctx :b})}
                                       :ctx {}
                                       :ctx-specs {:b (s/map-of #{:b} #{:b})}})]
                 (async/go
                   (let [failed (async/<! (async/into [] (xst/test-simple-paths model)))]
                     (doseq [f failed]
                       (is (nil? f)))
                     (done)))))))
