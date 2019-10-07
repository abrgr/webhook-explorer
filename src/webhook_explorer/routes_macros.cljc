(ns webhook-explorer.routes-macros
  (:require [secretary.core :refer [defroute]]))

(defmacro defextroute [hist path-name nav-name matcher params & body]
  `(do (defroute ~path-name ~matcher ~params ~@body)
       (defn ~nav-name [& args#]
          (.setToken ~hist (apply ~path-name args#)))))
