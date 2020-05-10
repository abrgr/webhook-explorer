(ns webhook-explorer.utils
  (:require [clojure.spec.alpha :as s]))

(s/def ::abort-item
  (s/cat :pred any?
         :value any?))
(s/def ::abort
  (s/cat :abort-glyph #{:abort}
         :abort-items (s/spec (s/+ ::abort-item))))
(s/def ::assignment
  (s/cat :id (s/or :sym symbol?
                   :map map?
                   :vec vector?)
         :val any?
         :opt (s/? ::abort)))
(s/def ::assignments
  (s/* ::assignment))

(defn let+* [conformed-assignments body]
  (if (empty? conformed-assignments)
    (if (= (count body) 1)
      (first body)
      body)
    (let [a (first conformed-assignments)
          id (-> a :id second)
          v (-> a :val)
          abort-items (->> a
                           :opt
                           :abort-items
                           (mapcat (juxt :pred :value)))
          body' (let+* (rest conformed-assignments) body)
          inner (if (empty? abort-items)
                  body'
                  `(cond ~@abort-items :default ~body'))]
      `(let [~id ~v]
         ~inner))))

(defmacro let+ [assignments & body]
  (let [conformed-assignments (s/conform ::assignments assignments)]
    (let+* conformed-assignments body)))

