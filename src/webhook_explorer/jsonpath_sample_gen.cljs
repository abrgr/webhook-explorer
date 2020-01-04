(ns webhook-explorer.jsonpath-sample-gen
  (:require ["jsonpath" :as jp]))

(defmulti ^:private gen (fn [{:keys [type]} _] type))

(defmethod gen :root
  (fn [_ obj]
    nil))
  

(defn gen-obj [path]
  (let [ast (jp/parse path)]
    ; this doesn't work because ast is a list, not an ast
    (gen ast {})))
