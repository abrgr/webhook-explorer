(ns webhook-explorer.xstate-test
  (:require [clojure.spec.test.alpha :as stest]
            [clojure.spec.gen.alpha]
            [clojure.test.check]
            [clojure.test.check.properties]
            [webhook-explorer.xstate :as xs]
            [cljs.test :refer-macros [deftest testing is]]))

(stest/instrument)

(def check-cfg {:clojure.spec.test.check/opts {:max-size 3 :num-tests 5}})

(defn gen-test [res]
  (is (->> res
           ((fn [x] (.log js/console x) x))
           (every? (comp not :failure)))))

(deftest state->js-states
  (testing "Generated state->js-states"
    (gen-test (stest/check `xs/state->js-states* check-cfg))))

(deftest state->js-state
  (testing "Generated state->js-state"
    (gen-test (stest/check `xs/state->js-state* check-cfg))))

(deftest state-def->state-names
  (testing "Generated state-def->state-names"
    (gen-test (stest/check `xs/state-def->state-names* check-cfg))))

(deftest transition-to->js-transition
  (testing "Generated transition-to->js-transition"
    (gen-test (stest/check `xs/transition-to->js-transition* check-cfg))))

(deftest state-def->js-transition
  (testing "Generated state-def->js-transition"
    (gen-test (stest/check `xs/state-def->js-transition* check-cfg))))

(deftest transition-to->js-on
  (testing "Generated transition-to->js-on"
    (gen-test (stest/check `xs/transition-to->js-on* check-cfg))))

(deftest transition-to->js-invoke
  (testing "Generated transition-to->js-invoke"
    (gen-test (stest/check `xs/transition-to->js-invoke* check-cfg))))

(deftest cfg->machine-test
  (testing "Generated cfg->machine"
    (gen-test (stest/check `xs/cfg->machine check-cfg))))
