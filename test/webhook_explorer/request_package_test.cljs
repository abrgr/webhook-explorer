(ns webhook-explorer.request-package-test
  (:require [webhook-explorer.request-package :as rp]
            [clojure.test.check.clojure-test :refer-macros [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
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

(defn crv [c r v] (str "{{" (name c) "." r "." v "}}"))

(defmulti add-item-to-req (fn [req [typ pos]] {:type typ :pos pos}))
(defmethod add-item-to-req {:type :input-dep :pos :headers}
  [req [_ pos _ k v]]
  (update-in req [pos k] #(str % "{{" v "}}")))
(defmethod add-item-to-req {:type :input-dep :pos :qs}
  [req [_ pos _ k v]]
  (update-in req [pos k] #(str % "{{" v "}}")))
(defmethod add-item-to-req {:type :input-dep :pos :body}
  [req [_ _ _ _ v]]
  (update req :body #(str % " {{" v "}}")))
(defmethod add-item-to-req {:type :req-dep :pos :headers}
  [req [_ pos k c r v]]
  (update-in req [pos k] #(str % (crv c r v))))
(defmethod add-item-to-req {:type :req-dep :pos :qs}
  [req [_ pos k c r v]]
  (update-in req [pos k] #(str % (crv c r v))))
(defmethod add-item-to-req {:type :req-dep :pos :body}
  [req [_ _ _ c r v]]
  (update req :body #(str % (crv c r v))))

(defn items-to-req [items]
  (reduce
    add-item-to-req
    {}
    items))

(defn input-deps-to-deps [input-deps]
  (->> input-deps
       (keep
         (fn [[src items]]
           (let [v (->> items
                        (keep
                          (fn [[typ pos _ c r v]]
                            (when (= typ :req-dep)
                              {:trigger c
                               :req r
                               :template-var v})))
                        (into #{}))]
            (when (not-empty v)
             [src v]))))
         (into {})))

(def ne-ascii (gen/such-that not-empty gen/string-alphanumeric))

(defspec dependency-graph-prop 20
  (prop/for-all [input-deps (gen/map
                              ne-ascii
                              (gen/vector
                                (gen/one-of
                                  [(gen/tuple
                                     (gen/elements [:input-dep])
                                     (gen/elements [:headers :qs :body])
                                     (gen/elements [:ignore])
                                     ne-ascii
                                     ne-ascii)
                                   (gen/tuple
                                     (gen/elements [:req-dep])
                                     (gen/elements [:headers :qs :body])
                                     ne-ascii
                                     (gen/elements [:every :all])
                                     ne-ascii
                                     ne-ascii)])))]
    (let [reqs (reduce
                 (fn [reqs [req-name items]]
                   (if (not-empty items)
                     (conj reqs {:name req-name :req (items-to-req items)})
                     reqs))
                 []
                 input-deps)
          result (rp/dependency-graph {:reqs reqs})
          expected (input-deps-to-deps input-deps)]
      (is (= result expected)))))
