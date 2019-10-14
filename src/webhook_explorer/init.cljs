(ns webhook-explorer.init)

(def ^:private init-routines (atom []))

(defn register-init [pri f]
  (swap! init-routines conj {:pri pri :f f}))

(defn fire-init []
  (doseq [{:keys [f]} (sort-by :pri @init-routines)]
    (f))
  (reset! init-routines nil))
