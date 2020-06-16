(ns webhook-explorer.xstate
  (:require-macros [webhook-explorer.xstate :refer [case]])
  (:require [clojure.spec.alpha :as s]
            [clojure.walk :as walk]
            [webhook-explorer.specs.xstate]
            ["xstate" :as xs]
            [goog.object :as obj]
            [reagent.core :as r]))

(declare state-def->js-state
         cfg->machine*
         js-state->clj
         js-svc->clj
         machine?)

(defn state-def->state-names [{:keys [transition delayed-transition]}]
  (->> (concat transition delayed-transition)
       (map :to)
       (map :target)
       (filter (comp (partial = :other) first))
       (map second)
       (map name)))

(defn state-def->state-names* [state-def]
  (state-def->state-names (s/conform :xstate/state-def state-def)))

(s/fdef state-def->state-names*
  :args (s/cat :state-def :xstate/state-def)
  :ret (s/coll-of string?))

(defn- get-mods-by-type [typ v mods]
  (->> mods
       (filter #(= (first %) typ))
       (map second)
       (map v)
       (map name)))

(defn transition-to->js-transition [{:keys [mods] [target-type target] :target}]
  (let [actions (get-mods-by-type :action :action mods)
        guards (get-mods-by-type :guard :guard mods)]
    (if (= target-type :forbidden)
      js/undefined
      (cond-> {}
        (= target-type :other) (assoc :target (name target))
        (= target-type :ext-self) (assoc :internal false)
        (namespace target) (assoc :internal (not= (namespace target) '*ext*))
        (not-empty actions) (assoc :actions actions)
        (not-empty guards) (assoc :guards guards)))))

(defn transition-to->js-transition* [to]
  (transition-to->js-transition (s/conform :xstate/transition-to to)))

(s/fdef transition-to->js-transition*
  :args (s/cat :to (s/spec :xstate/transition-to))
  :ret :xstate-js/transition)

(defn transition->js-on [{:keys [to] [event-type event] :event}]
  (let [transition (transition-to->js-transition to)]
    {(if (= event-type :transient) "" event)
     (if transition [transition] transition)}))

(defn transition->js-on* [transition]
  (transition->js-on (s/conform :xstate/transition transition)))

(s/fdef transition->js-on*
  :args (s/cat :transition :xstate/transition)
  :ret :xstate-js/on)

(defn delayed-transition->js-after [{:keys [delay-ms to]}]
  {delay-ms (transition-to->js-transition to)})

(def ^:private js-handler-by-type
  {:on-done :onDone
   :on-error :onError
   :data :data})

(defn evt->clj [evt]
  (if (obj/containsKey evt "_clj")
    (obj/get evt "_clj") ; anything sent with our send function has _clj
    (js->clj evt :keywordize-keys true)))

(defn handlers->js [handlers]
  (reduce
   (fn [js-handlers [handler-type {:keys [to data]}]]
     (assoc
      js-handlers
      (get js-handler-by-type handler-type)
      (cond
        to (transition-to->js-transition to)
        data (->> data
                  (into
                    {}
                    (map
                      (fn [[k data-fn]]
                        [k
                         (fn [js-ctx js-evt]
                           (data-fn (js->clj js-ctx :keywordize-keys true) (evt->clj js-evt)))])))
                  clj->js))))
   {}
   handlers))

(defn invocation->js-invoke [{:keys [service-name] [handler-type handlers] :handlers :as m}]
  [(-> {:id (name service-name)
        :src (name service-name)}
       (merge (handlers->js handlers)))])

(defn invocation->js-invoke* [invocation]
  (invocation->js-invoke (s/conform :xstate/invocation invocation)))

(s/fdef invocation->js-invoke*
  :args (s/cat :invocation :xstate/invocation)
  :ret :xstate-js/invoke)

(defn child-states->js-child-states [{:keys [cfg]}]
  (cfg->machine* cfg))

(defn- merge-state-part-vecs [state-key item-key item js-state]
  (update
   js-state
   state-key
   #(->> %
         (concat
          (->> item
               (mapcat item-key)
               (map name)))
         (into []))))

(defn state-def->js-state [state-def]
  (reduce
   (fn [js-state [descriptor item]]
     (clojure.core/case descriptor
       :transition (update js-state :on #(merge-with concat % (transition->js-on item)))
       :delayed-transition (update js-state :after merge (delayed-transition->js-after item))
       :invocation (update js-state :invoke #(->> %
                                                  (concat (invocation->js-invoke item))
                                                  (into [])))
       :entry-actions (merge-state-part-vecs :entry :action item js-state)
       :exit-actions (merge-state-part-vecs :exit :action item js-state)
       :activities (merge-state-part-vecs :activities :activity-names item js-state)
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

(defn cfg->machine* [{:keys [parallel any-state init-state unadorned-states final-states]}]
  (cond-> {:states (->> (concat (if init-state [init-state] []) unadorned-states)
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
                                 final-states)
                                [{}])
                        (apply merge))}
    init-state (assoc :initial (-> init-state :state :id name))
    (some? any-state) (assoc :on (-> any-state :def state-def->js-transition :on))
    (some? any-state) (update :states (partial merge-with #(or %1 %2)) (->> any-state
                                                                            :def
                                                                            state-def->state-names
                                                                            (map #(vector %1 nil))
                                                                            (into {})))
    parallel (assoc :type "parallel")))

(defn cfg->machine [id cfg]
  "Converts a state chart configuration into a config appropriate to pass to (machine).
   Example configuration:

     [* [[:evt-a -> :a ! :act-a | :guard-a]] ; in any state (*), on event evt-a, transition to state a and execute action act-a if guard-a is met
      > :idle [[*transient* -> :a]] ; initial state idle; on transient (empty) event, transition to state-a
      :a [[!+ :activity-a] ; in state-a, run activity-a
          [$ :svc-a
             :on-done -> :done
             :on-error -> :err]] ; in state-a, invoke svc-a with these handlers
      :done [(children
               > :b [[after 100 -> :c]] ; b is the init child state of done; transition to c after 100 ms
               x :c [])] ; final state c
      :err [[>! :entry-action] ; run entry-action on entry to err state
            [!> :exit-action] ; run exit-action on exit from err state
            :key :val]] ; add key=>val to the err state node

   Grammar description:

     map states to list of transitions, invocations, enter/exit actions, activities, child state maps, or :key :vals to attach to the node
     [] - means an actual list; \\[\\] means optional
     transition = [:event -> :target ! action \\[! action2...\\] | guard \\[| guard...\\]]
     self transition = [:event -> *self*]
     forced external transition = [:event -> *ext*/event]
     forced internal transition = [:event -> *int*/event]
     external self transition = [:event -> *ext*/*self*]
     delayed transition = [after 100 -> :target ...]
     invocation = [$ :service-name [...transitions (e.g. on-done, on-error) or :key :val]] ; id defaults to src
     entry actions = [>! :action-name \\[:action-name2...\\]]
     exit actions = [!> :action-name \\[:action-name2...\\]]
     activity = [!+ :activity-name \\[:activity-name2...\\]]
     child state = (children > :state {} ...)
     * indicates any state
     > indicates initial state
     x indicates final state
     || as the first entry indicates parallel nodes
  "
  (let [c (s/conform (s/spec :xstate/config) cfg)]
    (when (s/invalid? c)
      (tap> {:sender ::cfg->machine
             :msg :failed-cfg-spec
             :id id
             :explanation (s/explain-data (s/spec :xstate/config) cfg)})
      (throw (js/Error. "Bad state machine config")))
    (-> c
        cfg->machine*
        (assoc :id (name id)))))

(s/fdef cfg->machine
  :args (s/cat :id keyword? :cfg (s/spec :xstate/config))
  :ret :xstate-js/machine)

(def assign xs/assign)

(defn js-svc->clj
  ([svc]
   (js-svc->clj svc {}))
  ([svc extra-meta]
   (let [machine (obj/get svc "machine")
         config (-> machine (obj/get "config") (js->clj :keywordize-keys true))
         opts (-> machine (obj/get "options") (js->clj :keywordize-keys true))]
     ^{:js-cfg (update extra-meta :js-cfg merge config opts (js->clj (obj/get svc "options")))}
     {:svc svc})))

(defn interpret-and-start [machine opts]
  (let [svc (js-svc->clj
             (xs/interpret (:m machine) (clj->js (assoc opts :devTools js/goog.DEBUG)))
             (meta machine))]
    (.start (:svc svc))
    svc))

(defn- make-opt-val-fn [f]
  (fn
    ([ctx evt]
     (f (js->clj ctx :keywordize-keys true) (evt->clj evt)))
    ([ctx evt meta]
     (f (js->clj ctx :keywordize-keys true) (evt->clj evt) (js->clj meta :keywordize-keys true)))))

(defn- xform-machine-opt-vals [opt-vals]
  (into
    {}
    (map
     (fn [[n v]]
       [n
        (cond
          (fn? v) (make-opt-val-fn v)
          (machine? v) (:m v)
          :else v)]))
    opt-vals))

(defn machine->js-cfg [machine]
  (-> machine
      meta
      :js-cfg))

(s/fdef machine->js-cfg
  :args (s/cat :machine :xstate/machine)
  :ret :xstate-js/machine)

(defn machine [{:keys [cfg opts]}]
  ^{:js-cfg (merge cfg opts)}
  {:m (cond-> (xs/Machine
               (clj->js cfg)
               (-> opts
                   (update :guards xform-machine-opt-vals)
                   (update :actions xform-machine-opt-vals)
                   (update :services xform-machine-opt-vals)
                   clj->js))
        (:ctx opts) (.withContext (-> opts :ctx clj->js)))})

(defn machine? [maybe-machine]
  ((every-pred :m (comp :js-cfg meta)) maybe-machine))

(defn js-inner-machine->clj [form]

  (if (instance? xs/Interpreter form)
    (assoc
     (js-svc->clj form)
     :state
     (js-state->clj (obj/get form "state")))
    form))

(defn js-state->clj [state]
  (-> ["context" "activities" "actions" "meta" "value" "event" "done" "changed"]
      (->> (reduce
            (fn [acc k]
              (assoc acc (keyword k) (js->clj (obj/get state k) :keywordize-keys true)))
            ^{:js-state state} {}))
      (update :context (partial walk/postwalk js-inner-machine->clj))))

(defn clj-state->js [state]
  (-> state meta :js-state))

(defn shallow-clj->js [obj]
  (apply
    js-obj
    (mapcat
      (fn [[k v]] [(name k) v])
      obj)))

(defn assign-ctx [{:keys [ctx-prop static-ctx]}]
  (-> {ctx-prop (constantly static-ctx)}
      shallow-clj->js
      xs/assign))

(defn assign-ctx-from-evt [{:keys [evt-prop evt-path ctx-prop static-ctx]}]
  (-> static-ctx
      (assoc ctx-prop (fn [_ evt]
                        (-> evt
                            evt->clj
                            (get-in (if evt-prop [evt-prop] evt-path)))))
      shallow-clj->js
      xs/assign))

(defn xform-ctx [{:keys [ctx-prop static-ctx]} update-fn & update-args]
  (-> static-ctx
      (assoc
       ctx-prop
       (fn [ctx _]
         (apply
          update-fn
          (obj/get ctx (name ctx-prop))
          update-args)))
      shallow-clj->js
      xs/assign))

(defn xform-ctx-from-evt [{:keys [ctx-prop static-ctx]} update-fn & update-args]
  (-> static-ctx
      (assoc
       ctx-prop
       (fn [ctx evt]
         (apply
          update-fn
          (obj/get ctx (name ctx-prop))
          (evt->clj evt)
          update-args)))
      shallow-clj->js
      xs/assign))

(defn ->action [f]
  (fn [ctx evt]
    (f (js->clj ctx :keywordize-keys true) (evt->clj evt))))

(defn update-ctx-from-evt [{:keys [ctx-prop updater-prop static-ctx]}]
  (-> static-ctx
      (assoc
       ctx-prop
       (fn [ctx evt]
         (let [[update-fn & update-args] (-> evt evt->clj updater-prop)]
           (apply
            update-fn
            (obj/get ctx (name ctx-prop))
            update-args))))
      shallow-clj->js
      xs/assign))

(defn spawn
  ([actor]
   (xs/spawn (:m actor)))
  ([actor opts]
   (xs/spawn (:m actor) (clj->js opts))))

(defn clj-evt->js [evt]
  (cond
    (ident? evt) (name evt)
    (string? evt) evt
    (map? evt) (->> evt
                    (mapcat
                     (fn [[k v]]
                       [(name k) (if (ident? v) (name v) v)]))
                    (concat ["_clj" evt])
                    (apply js-obj))))

(defn send-event [event target-fn]
  (xs/send
   (if (fn? event)
     (fn [ctx evt]
       (-> (event
            (js->clj ctx :keywordize-keys true)
            (js->clj evt :keywordize-keys true))
           clj-evt->js))
     event)
   #js {:to (fn [ctx] (target-fn (js->clj ctx :keywordize-keys true)))}))

(defn replace-cfg [m cfg]
  (machine {:cfg cfg :opts (js->clj (obj/get (:m m) "options") :keywordize-keys true)}))

(defn with-cfg [machine cfg]
  (with-meta
    {:m (.withConfig (:m machine) (clj->js cfg))}
    (update (meta machine) :js-cfg merge cfg)))

(defn with-ctx [machine ctx]
  (with-meta
    {:m (.withContext (:m machine) (clj->js ctx))}
    (meta machine)))

(defn with-svc [{{:keys [svc]} :svc} _]
  (let [s (r/atom  (-> svc (obj/get "state") js-state->clj))]
    (.onTransition
     svc
     #(->> % js-state->clj (reset! s)))
    (fn [_ child]
      (r/as-element (child @s)))))

(defn send [{:keys [svc]} evt]
  (.send svc (clj-evt->js evt)))

(defn matches? [state test-state]
  (and state (.matches (clj-state->js state) (name test-state))))
