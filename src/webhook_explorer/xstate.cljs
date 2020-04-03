(ns webhook-explorer.xstate
  (:require-macros [webhook-explorer.xstate :refer [case]])
  (:require [clojure.spec.alpha :as s]
            [webhook-explorer.specs.xstate]
            ["xstate" :as xs]
            [goog.object :as obj]
            [reagent.core :as r]))

(defn state-def->state-names [{:keys [transition delayed-transition]}]
  (->> (concat transition delayed-transition)
       (map :to)
       (map :target)
       (map name)))

(defn state-def->state-names* [state-def]
  (state-def->state-names (s/conform :xstate/state-def state-def)))

(s/fdef state-def->state-names*
  :args (s/cat :state-def :xstate/state-def)
  :ret (s/coll-of string?))

(defn- get-mods-by-type [type val mods]
  (->> mods
       (filter #(= (first %) type))
       (map second)
       (map val)))

(defn transition-to->js-transition [{:keys [target mods]}]
  (let [actions (get-mods-by-type :actions :action mods)
        guards (get-mods-by-type :guards :guard mods)]
    (cond-> {:target (name target)}
      (not-empty actions) (assoc :actions actions)
      (not-empty guards) (assoc :guards guards))))

(defn transition-to->js-transition* [to]
  (transition-to->js-transition (s/conform :xstate/transition-to to)))

(s/fdef transition-to->js-transition*
  :args (s/cat :to (s/spec :xstate/transition-to))
  :ret :xstate-js/transition)

(defn transition->js-on [{:keys [to] [event-type event] :event}]
  {(if (= event-type :transient) "" event)
   [(transition-to->js-transition to)]})

(defn transition->js-on* [transition]
  (transition->js-on (s/conform :xstate/transition transition)))

(s/fdef transition->js-on*
  :args (s/cat :transition :xstate/transition)
  :ret :xstate-js/on)

(defn delayed-transition->js-after [{:keys [delay-ms to]}]
  {delay-ms (transition-to->js-transition to)})

(defn promise-handlers->js [{:keys [on-done on-error]}]
  (cond-> {}
    on-done (assoc :onDone (transition-to->js-transition (:to on-done)))
    on-error (assoc :onError (transition-to->js-transition (:to on-error)))))

(defn machine-handlers->js [{:keys [on-done data]}]
  (cond-> {}
    on-done (assoc :onDone (transition-to->js-transition (:to on-done)))
    data (assoc :data (:data data))))

(defn invocation->js-invoke [{:keys [service-name handlers]}]
  (cond-> {:id (name service-name)
           :src (name service-name)}
    (:promise handlers) (merge (promise-handlers->js (:promise handlers)))
    (:machine handlers) (merge (machine-handlers->js (:machine handlers)))))

(defn invocation->js-invoke* [invocation]
  (invocation->js-invoke (s/conform :xstate/invocation invocation)))

(s/fdef invocation->js-invoke*
  :args (s/cat :invocation :xstate/invocation)
  :ret :xstate-js/invoke)

(declare state-def->js-state)

(defn child-states->js-child-states [children]
  (reduce
   (fn [states [[state-type state-id] state-def]]
     (if (= state-type :parallel)
       (assoc states :type "parallel") ; TODO: handle final states
       (assoc-in states [:states state-id] (state-def->js-state state-def))))
   {}
   children))

(defn state-def->js-state [state-def]
  (reduce
   (fn [js-state [descriptor item]]
     (clojure.core/case descriptor
       :transition (assoc js-state :on (apply merge-with concat (map transition->js-on item)))
       :delayed-transition (assoc js-state :after (apply merge (map delayed-transition->js-after item)))
       :invocation (assoc js-state :invoke (map invocation->js-invoke item))
       :entry-actions (assoc js-state :entry (->> item
                                                  (mapcat :action)
                                                  (map name)))
       :exit-actions (assoc js-state :exit (->> item
                                                (mapcat :action)
                                                (map name)))
       :activities (assoc js-state :activities (->> item
                                                    (mapcat :activity-names)
                                                    (map name)))
       :child-states (merge js-state (child-states->js-child-states item))
       :extra-cfg (assoc js-state (:key item) (:value item))))
   {}
   state-def))

(defn state-def->js-state* [state-def]
  (state-def->js-state (s/conform :xstate/state-def state-def)))

(s/fdef state-def->js-state*
  :args (s/cat :state-def :xstate/state-def)
  :ret :xstate-js/state)

(defn state-def->js-transition [state-def]
  (-> state-def
      state-def->js-state
      (select-keys [:on])))

(defn state-def->js-transition* [state-def]
  (state-def->js-transition (s/conform :xstate/state-def state-def)))

(s/fdef state-def->js-transition*
  :args (s/cat :state-def (s/spec :xstate/state-def))
  :ret (s/keys :opt-un [:xstate-js/on]))

(defn state->js-states [{:keys [id def]}]
  {id (state-def->js-state def)})

(defn state->js-states* [state]
  (state->js-states (s/conform :xstate/state state)))

(s/fdef state->js-states*
  :args (s/cat :state (s/spec :xstate/state))
  :ret :xstate-js/states)

(defn cfg->machine [id cfg]
  (let [c (s/conform :xstate/config cfg)]
    (when (s/invalid? c)
      (tap> (s/explain-data :xstate/config cfg))
      (throw (js/Error. "Bad config")))
    (let [{:keys [parallel any-state init-state unadorned-states final-states]} c]
      (cond-> {:id (name id)
               :initial (-> init-state :state :id name)
               :states (->> (concat [init-state] unadorned-states)
                            (map :state)
                            (map state->js-states)
                            (concat (map
                                     (comp
                                      (partial into {})
                                      #(map
                                        (fn [[k v]]
                                          [k (assoc v :type :final)])
                                        %)
                                      state->js-states
                                      :state)
                                     final-states))
                            (apply merge))}
        (some? any-state) (assoc :on (-> any-state :def state-def->js-transition :on))
        (some? any-state) (update :states (partial merge-with #(or %1 %2)) (->> any-state
                                                                                :def
                                                                                state-def->state-names
                                                                                (map #(vector %1 nil))
                                                                                (into {})))
        parallel (assoc :type "parallel")))))

(s/fdef cfg->machine
  :args (s/cat :id keyword? :cfg :xstate/config)
  :ret :xstate-js/machine)

(def assign xs/assign)

(defn interpret-and-start [machine opts]
  (let [svc ^{:js-cfg (-> machine meta (update :js-cfg merge opts))}
        {:svc (xs/interpret (:m machine))}]
    (.start (:svc svc))
    svc))

(defn- xform-opt-fns [opt-fns]
  (->> opt-fns
       (map
        (fn [[n f]]
          [n
           (if (fn? f)
             (fn
               ([ctx evt]
                (f (clj->js ctx) (clj->js evt)))
               ([ctx evt meta]
                (f (clj->js ctx) (clj->js evt) (clj->js meta))))
             f)]))
       (into {})))

(defn machine->js-cfg [machine]
  (-> machine
      meta
      :js-cfg))

(s/fdef machine->js-cfg
  :args (s/cat :machine :xstate/machine)
  :ret :xstate-js/machine)

(defn machine [{:keys [cfg opts]}]
  ^{:js-cfg (merge cfg opts)}
  {:m (xs/Machine
       (clj->js cfg)
       (-> opts
           (update :guards xform-opt-fns)
           (update :actions xform-opt-fns)
           (update :services xform-opt-fns)
           clj->js))})

(defn assign-ctx [{:keys [ctx-prop static-ctx]}]
  (-> {ctx-prop (constantly static-ctx)}
      clj->js
      xs/assign))

(defn assign-ctx-from-evt [{:keys [evt-prop ctx-prop static-ctx]}]
  (-> static-ctx
      (assoc ctx-prop (fn [_ e] (obj/get e (name evt-prop))))
      clj->js
      xs/assign))

(defn update-ctx-from-evt [{:keys [ctx-prop updater-prop static-ctx]}]
  (-> static-ctx
      (assoc
       ctx-prop
       (fn [ctx e]
         (let [[update-fn & update-args] (obj/get e (name updater-prop))]
           (apply
            update-fn
            (obj/get ctx (name ctx-prop))
            update-args))))
      clj->js
      xs/assign))

(defn replace-cfg [machine cfg]
  (with-meta
    {:m (machine {:cfg cfg :opts (obj/get (:m machine) "options")})}
    (assoc (meta machine) :js-cfg cfg)))

(defn with-cfg [machine cfg]
  (with-meta
    {:m (.withConfig (:m machine))}
    (update (meta machine) :js-cfg merge cfg)))

(defn with-ctx [machine ctx]
  (with-meta
    {:m (.withContext (:m machine))}
    (meta machine)))

(defn with-svc [{:keys [svc]} _]
  (let [s (r/atom  (-> svc (obj/get "state") js->clj))]
    (.onTransition svc #(->> % js->clj (reset! s)))
    (fn [_ child]
      (r/as-element (child @s)))))

(defn send [{:keys [svc]} evt]
  (.send
   svc
   (->> evt
        (mapcat
         (fn [[k v]]
           [(name k) (if (ident? v) (name v) v)]))
        (apply js-obj))))

(defn matches? [state test-state]
  (.matches state (name test-state)))
