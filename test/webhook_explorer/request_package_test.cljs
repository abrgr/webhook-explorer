(ns webhook-explorer.request-package-test
  (:require [webhook-explorer.request-package :as rp]
            [cljs.spec.test.alpha :as stest]
            [clojure.core.async :as async]
            [clojure.test.check.clojure-test :refer-macros [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [cljs.test :refer-macros [deftest testing is] :as tst]))

(stest/instrument)

(deftest acyclical-test
  (testing "acyclical"
    (is (not (rp/acyclical? {"a" #{{:trigger :every :req "b" :template-var "x"}}
                             "b" #{{:trigger :all :req "c" :template-var "x"}}
                             "c" #{{:trigger :every :req "a" :template-var "x"}}})))

    (is (rp/acyclical? {"b" #{{:trigger :every :req "a" :template-var "x"}}
                        "c" #{{:trigger :all :req "b" :template-var "x"}}}))

    (is (rp/acyclical? {"a" #{{:trigger :every :req "b" :template-var "x"}
                              {:trigger :every :req "c" :template-var "x"}}
                        "b" #{{:trigger :all :req "c" :template-var "x"}}
                        "c" #{{:trigger :every :req "d" :template-var "x"}}}))))

(deftest dependency-graph
  (testing "dependency-graph"
    (let [r1 (rp/dependency-graph {:name "g"
                                   :input-template-vars []
                                   :reqs [{:name "a"
                                           :req {:headers {:h1 "{{hi}}"
                                                           :h2 "{{{every.req1.a}}}"}
                                                 :qs {:q1 "{{#all.req1.b}}{{.}}{{/all.req1.b}}"}
                                                 :body "{{every.req2.c}}"}}
                                          {:name "b"
                                           :req {:headers {:h1 "{{every.req3.a}}"}
                                                 :qs {:q1 "{{all.req1.this_thing_here}}"}
                                                 :body "{{every.req1.c}}"}}]})]
      (is (= r1 {"a" #{{:trigger :every :req "req1" :template-var "a" :plural false}
                       {:trigger :all :req "req1" :template-var "b" :plural true}
                       {:trigger :every :req "req2" :template-var "c" :plural false}}
                 "b" #{{:trigger :every :req "req3" :template-var "a" :plural false}
                       {:trigger :all :req "req1" :template-var "this_thing_here" :plural false}
                       {:trigger :every :req "req1" :template-var "c" :plural false}}})))))

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
                             :template-var v
                             :plural false})))
                       (into #{}))]
            (when (not-empty v)
              [src v]))))
       (into {})))

(def reg-str (gen/such-that not-empty gen/string-alphanumeric))

(defspec dependency-graph-prop 20
  (prop/for-all [input-deps (gen/map
                             reg-str
                             (gen/vector
                              (gen/one-of
                               [(gen/tuple
                                 (gen/elements [:input-dep])
                                 (gen/elements [:headers :qs :body])
                                 (gen/elements [:ignore])
                                 reg-str
                                 reg-str)
                                (gen/tuple
                                 (gen/elements [:req-dep])
                                 (gen/elements [:headers :qs :body])
                                 reg-str
                                 (gen/elements [:every :all])
                                 reg-str
                                 reg-str)])))]
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

(deftest get-dep-vals-test
  (testing "get-dep-vals"
    (is
     (= {"a" {"x" 4}
         "b" {"y" 9}}
        (rp/get-dep-vals
         [{:req "a" :id "1"}
          {:req "b" :id "6"}]
         {"a" {[{:req "a" :id "1"}] {"x" 4}
               [{:req "a" :id "2"}] {"x" 5}}
          "b" {[{:req "a" :id "2"}
                {:req "b" :id "3"}] {"y" 7}
               [{:req "a" :id "1"}
                {:req "b" :id "6"}] {"y" 9}}})))))

(deftest run-pkg-test
  (testing "run-pkg"
    (tst/async
     done
     (let [reqs [{:name "a"
                  :req {:headers {"h1" "{{inp_a}}"}}
                  :captures {:headers {"x" {:template-var "x"}}}}
                 {:name "b"
                  :req {:headers {"h1" "{{every.a.x}}"}}
                  :captures {:body {:type :json
                                    :captures {"$.x" {:template-var "x"}}}}}
                 {:name "c"
                  :req {:headers {"h1" "{{#all.b.x}}{{.}}{{/all.b.x}}"}}
                  :captures {:body {:type :json
                                    :captures {"$.x" {:template-var "x"}}}}}]
           invocations (atom [])
           rp-ch  (rp/run-pkg {:inputs {"inp_a" "hello"}
                               :exec (fn [req]
                                       (async/go
                                         (swap! invocations conj {:req req})
                                         (if (= (:name req) "a")
                                           {:headers {"x" ["val1" "val2" "val3"]}}
                                           {:body {"x" "hello"}})))
                               :pkg {:reqs reqs}})]
       (async/take!
        rp-ch
        (fn []
          (is (= (mapv #(get-in % [:req :name]) @invocations) ["a" "b" "b" "b" "c"]))
          (is (= (get-in @invocations [0 :req :req :headers "h1"]) "hello"))
          (is (= (get-in @invocations [1 :req :req :headers "h1"]) "val1"))
          (is (= (get-in @invocations [2 :req :req :headers "h1"]) "val2"))
          (is (= (get-in @invocations [3 :req :req :headers "h1"]) "val3"))
          (is (= (get-in @invocations [4 :req :req :headers "h1"]) "hellohellohello"))
          (done)))))))
