(ns webhook-explorer.routes
  (:require [secretary.core :refer [defroute]]))

(defmacro defextroute [hist page-id path-name nav-name pre-actions matcher params & body]
  (let [action-invocations (mapcat (fn [a] [`(~a) nil]) pre-actions)
        body-with-actions `(cond ~@action-invocations :else (do ~@body))]
    `(do (defroute ~path-name ~matcher ~params ~body-with-actions)
         (defmethod webhook-explorer.nav-to/go
           ~page-id
           ([_#]
             (.setToken ~hist (~path-name)))
           ([_# args#]
             (.setToken ~hist (~path-name args#))))
         (defn ~nav-name [& args#]
           (.setToken ~hist (apply ~path-name args#))))))
