(ns webhook-explorer.xstate-testing
  (:require [clojure.spec.alpha :as s]
            [clojure.core.async :as async]
            [goog.object :as obj]
            ["@xstate/test" :as xst]
            [webhook-explorer.specs.xstate]
            [webhook-explorer.xstate :as xs]))

(defn- handle-failed-test [explanation state]
  (when explanation
    (tap> {:sender ::tester
           :msg :failed-context-spec
           :state state
           :explanation explanation})
    (throw (js/Error. "Failed context spec"))))

(defn- make-test [existing-test state ctx-spec-or-child-map global-ctx-spec]
  (let [ctx-spec (if (s/spec? ctx-spec-or-child-map)
                   ctx-spec-or-child-map
                   (get ctx-spec-or-child-map :xstate-test/spec))]
    (fn [test-ctx state]
      (existing-test test-ctx state)
      (-> state
          (obj/get "context")
          (js->clj :keywordize-keys true)
          (->> (s/explain-data (s/and ctx-spec global-ctx-spec)))
          (handle-failed-test state)))))

(defn- add-tests-to-states [states ctx-spec ctx-specs]
  (->> states
       (map
        (fn [state state-def]
          [state
           (-> state-def
               (update-in [:meta :test] make-test state (get ctx-specs state) ctx-spec)
               (update :states add-tests-to-states (get ctx-specs state)))]))
       (into {})))

(defn with-tests [machine {:keys [ctx-specs ctx-spec]}]
  (->> machine
       xs/machine->js-cfg
       :states
       (add-tests-to-states ctx-spec ctx-specs)
       (xs/replace-cfg machine)))

(defn model [{:keys [machine ctx] :as cfg}]
  (cond-> machine
    cfg (with-tests cfg)
    cfg (xs/with-cfg cfg)
    ctx (xs/with-ctx ctx)
    :m
    xst/create-model))

(s/fdef model
  :args (s/cat :cfg :xstate-test/cfg)
  :ret (partial instance? xst/TestModel))

(defn test-simple-paths [model]
  (let [plans-chan (async/to-chan (.getSimplePathPlans model))
        paths-chan (async/chan)
        results-chan (async/chan)]
    (async/go-loop []
      (when-let [plan (async/<! plans-chan)]
        (->> (obj/get plan "paths")
             (array-seq)
             (map
              (fn [path]
                {:path path
                 :description (str (obj/get plan "description")
                                   "::"
                                   (obj/get path "description"))}))
             (async/onto-chan paths-chan))
        (recur))
      (async/close! paths-chan))
    (async/go-loop []
      (when-let [{:keys [path description]} (async/<! paths-chan)]
        (let [p (.test path)
              c (async/chan)]
          (.then p #(async/put! c :passed))
          (.catch p #(async/put! c :failed))
          (let [res (async/<! c)]
            (if (= res :failed)
              (async/>! results-chan description)))
          (recur)))
      (async/close! results-chan))
    results-chan))
