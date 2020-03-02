(ns webhook-explorer.xstate
  (:require-macros [webhook-explorer.xstate :refer [case]])
  (:require ["xstate" :as xs]
            [goog.object :as obj]
            [reagent.core :as r]))

(def assign xs/assign)

(def interpret xs/interpret)

(defn- xform-opt-fns [opt-fns]
  (->> opt-fns
       (map
         (fn [[n f]]
           [n
            (if (fn? f)
              (fn
                ([ctx evt]
                  (f (clj->js ctx) (clj->js evt)))
                ([ctx evt meta]
                  (f (clj->js ctx) (clj->js evt) (clj->js meta))))
              f)]))
       (into {})))

(defn machine [{:keys [cfg opts]}]
  (xs/Machine
    (clj->js cfg)
    (-> opts
        (update :guards xform-opt-fns)
        (update :actions xform-opt-fns)
        (update :services xform-opt-fns)
        clj->js)))

(defn assign-ctx [{:keys [ctx-prop static-ctx]}]
  (-> {ctx-prop (constantly static-ctx)}
      clj->js
      xs/assign))

(defn assign-ctx-from-evt [{:keys [evt-prop ctx-prop static-ctx]}]
  (-> static-ctx
      (assoc ctx-prop (fn [_ e] (obj/get e (name evt-prop))))
      clj->js
      xs/assign))

(defn update-ctx-from-evt [{:keys [ctx-prop updater-prop static-ctx]}]
  (-> static-ctx
      (assoc
        ctx-prop
        (fn [ctx e]
          (let [[update-fn & update-args] (obj/get e (name updater-prop))]
            (apply
              update-fn
              (obj/get ctx (name ctx-prop))
              update-args))))
      clj->js
      xs/assign))

(defn with-svc [{:keys [svc]} _]
  (let [s (r/atom  (-> svc (obj/get "state") js->clj))]
    (.onTransition svc #(->> % js->clj (reset! s)))
    (fn [_ child]
      (r/as-element (child @s)))))

(defn send [interpreter evt]
  (.send
    interpreter
    (->> evt
         (mapcat
           (fn [[k v]]
             [(name k) (if (ident? v) (name v) v)]))
         (apply js-obj))))

(defn matches? [state test-state]
  (.matches state (name test-state)))
