(ns webhook-explorer.request-package-test
  (:require [webhook-explorer.request-package :as rp]
            [cljs.test :refer-macros [deftest testing is]]))

(deftest dependency-graph
  (testing "dependency-graph"
    (let [r1 (rp/dependency-graph {:reqs [{:name "a"
                                           :req {:headers {:h1 "{{hi}}"
                                                           :h2 "{{{every.req1.a}}}"}
                                                 :qs {:q1 "{{#all.req1.b}}{{.}}{{/all.req1.b}}"}
                                                 :body "{{every.req2.c}}"}}
                                          {:name "b"
                                           :req {:headers {:h1 "{{every.req3.a}}"}
                                                 :qs {:q1 "{{all.req1.this_thing_here}}"}
                                                 :body "{{every.req1.c}}"}}]})]
      (is (= r1 {"a" #{{:trigger :every :req "req1" :template-var "a"}
                       {:trigger :all :req "req1" :template-var "b"}
                       {:trigger :every :req "req2" :template-var "c"}}
                 "b" #{{:trigger :every :req "req3" :template-var "a"}
                       {:trigger :all :req "req1" :template-var "this_thing_here"}
                       {:trigger :every :req "req1" :template-var "c"}}})))))
