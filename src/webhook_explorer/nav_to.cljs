(ns webhook-explorer.nav-to)

(defmulti go (fn [page params] page))
