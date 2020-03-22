(ns webhook-explorer.xstate-test
  (:require [clojure.spec.test.alpha :as stest]
            [clojure.spec.gen.alpha] 
            [clojure.test.check]
            [clojure.test.check.properties] 
            [webhook-explorer.xstate :as xs]
            [cljs.test :refer-macros [deftest testing is]]))

(deftest cfg->machine-test
  (testing "Generated"
    (is (->> (stest/check `xs/cfg->machine)
             ((fn [x] (.log js/console x) x))
             (every? (comp not :failure))))))
