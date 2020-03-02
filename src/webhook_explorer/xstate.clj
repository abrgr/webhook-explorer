(ns webhook-explorer.xstate)

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
