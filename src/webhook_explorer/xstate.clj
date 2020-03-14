(ns webhook-explorer.xstate
  (:refer-clojure :exclude [case]))

(defmacro case [state & clauses]
  (let [adapted-clauses (->> clauses
                             (partition-all 2)
                             (mapcat
                              (fn [clause]
                                (if (= (count clause) 2)
                                  [(list 'webhook-explorer.xstate/matches? state (first clause))
                                   (second clause)]
                                  [:else (first clause)]))))]
    (conj adapted-clauses `cond)))

(comment
  "Desired state machine syntax"
    ; * = any state
    ; map states to list of transitions, invocations, enter/exit actions, activities, child state maps, or :key :vals to attach to the node
    ; [] - means an actual list; \[\] means optional
    ; transition = [:event -> :target @ action \[@ action2...\] | guard \[| guard...\]]
    ; delayed transition = [~ 100 -> :target ...]
    ; invocation = [$ :service-name [...transitions (e.g. on-done, on-error) or :key :val]] ; id defaults to src
    ; enter = ['@ :action-name]
    ; exit = [@' :action-name]
    ; activity = [@_ :activity-name \[:activity-name2\]
    ; > indicates initial state
    ; x indicates final state
    ; * is used to indicate transitions from any state
    ; || as the first entry OR as any key in a child state map indicates parallel nodes
    * [[:reset -> :start @ :reset-params]]
    > :idle []
    :start [[*transient* -> [:fetch-handler | :has-params]
                            [:ready @ :set-default-handler]]]
    :fetch-handler [$ :fetch-handler [[:on-done -> :ready @ :receive-handler]
                                      [:on-error -> :failed @ :receive-handler-error]]]
    :ready [[:update-handler @ :update-handler]]
    :fake-machine-state [$ :machine-to-invoke :data {:duration (fn [ctx evt] (:duration ctx))}]
    x :fake-final-state [:type :final]
  )
