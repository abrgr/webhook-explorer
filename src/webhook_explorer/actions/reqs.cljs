(ns webhook-explorer.actions.reqs
  (:require [webhook-explorer.app-state :as app-state]))

(defn toggle-expand [id]
  (swap!
    app-state/reqs
    update
    :expanded-reqs
    #(if (% id)
         (disj % id)
         (conj % id))))
