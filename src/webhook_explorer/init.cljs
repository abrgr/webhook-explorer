(ns webhook-explorer.init)

(def ^:private init-routines (atom []))

(defn register-init [f]
  (swap! init-routines conj f))

(defn fire-init []
  (doseq [i @init-routines] (i))
  (reset! init-routines nil))
