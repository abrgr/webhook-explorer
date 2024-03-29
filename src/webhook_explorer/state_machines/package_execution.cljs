(ns webhook-explorer.state-machines.package-execution
  (:require [clojure.core.async :as async]
            [clojure.walk :as walk]
            [debux.cs.core :as d :refer-macros  [dbg dbgn]]

            ; TODO: test is getting pulled in to check fspecs on conforming. weird
            [clojure.test.check :as stest]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]

            [webhook-explorer.xstate :as xs]
            [webhook-explorer.env :as env]
            [webhook-explorer.utils :as u]
            [webhook-explorer.promise-utils :as putil]
            [webhook-explorer.remote.packages :as remote-pkgs]
            [webhook-explorer.remote.s3-load :as s3-load]
            [webhook-explorer.state-machines.execute-package :as execpkg-machine]
            [goog.object :as obj]))

(defn load-execution [{:keys [request-url result-url] :as execution}]
  (->> [(s3-load/request {:method :get :url request-url :with-credentials? false})
        (s3-load/request {:method :get :url result-url :with-credentials? false})]
       u/async-merge-ordered
       (async/into [])
       (u/async-xform
        (map
         (fn [[{req-status :status req-body :body}
               {res-status :status res-body :body}]]
           (merge
            execution
            {:req (when (= req-status 200)
                    req-body)
             :res (when (= res-status 200)
                    res-body)}))))
       (u/async-xform
        (map
         (fn [execution]
           {:execution execution})))))

(def fully-loaded-execution?
  (every-pred :req :res))

(def pointer-loading-machine
  (xs/machine
   {:cfg
    (xs/cfg->machine
     :pointer-loader
     (walk/postwalk
      (fn [form]
        (if (= form 'ctx->executions)
          (fn [ctx evt] (obj/get ctx "executions"))
          form))
      '[> :start [[$ :load-next-execution-batch
                   :on-done -> :some-loaded ! :store-executions
                   :on-error -> :error]]
        :some-loaded [[*transient* -> :all-loaded | :all-loaded?]
                      [*transient* -> :retry-soon]]
        :retry-soon [[after 5000 -> :start]]
        :error []
        x :all-loaded [:data {:executions ctx->executions}]]))
    :opts
    {:ctx {:executions []}
     :actions
     {:store-executions
      (xs/assign-ctx-from-evt
       {:ctx-prop :executions
        :evt-path [:data :executions]})}
     :guards
     {:all-loaded? (fn [{:keys [executions]}]
                     (every? fully-loaded-execution? executions))}
     :services
     {:load-next-execution-batch
      (fn [{:keys [executions]}]
        (->> executions
             (filter (complement fully-loaded-execution?))
             (take 6)
             (map load-execution)
             async/merge
             (async/into [])
             (u/async-xform
              (map
               (fn [items]
                 {:executions (mapv :execution items)})))
             putil/chan->promise))}}}))

(defn merge-cols [m m1]
  (into
   m
   (map (fn [[k]] [k {:label (name k)}]))
   m1))

(def machine
  (xs/machine
   {:cfg
    (xs/cfg->machine
     :execution
     (walk/postwalk
      (fn [form]
        (if (= form 'needed-executions)
          (fn [{:keys [executions]} _]
            (filterv
             (complement (every-pred :req :res))
             executions))
          form))
      '[* [[:reset -> :loading ! :reset-params]
           [:load-executions -> :loading]]
        > :ready []
        :pointers-loaded [[$ :pointer-loading-machine
                           :on-done -> :ready ! :merge-executions ! :update-cols
                           :data {:executions needed-executions}]]
        :error [[:load-executions -> :loading]]
        :loading [[$ :load-executions
                   :on-done -> :pointers-loaded ! :store-executions ! :store-next-req
                   :on-error -> :error ! :store-error]
                  [:load-executions -> *forbidden*]]]))
    :opts
    {:ctx {:executions []
           :cols {:inputs (array-map :date {:label "Date"}) :outputs (array-map)}
           :params nil
           :next-req {}
           :error nil}
     :actions
     {:reset-params
      (xs/assign-ctx-from-evt {:evt-prop :params
                               :ctx-prop :params
                               :static-ctx {:executions []
                                            :next-req {}
                                            :error nil}})
      :store-executions
      (xs/xform-ctx-from-evt
       {:ctx-prop :executions}
       (fn [prev-executions {{:keys [executions]} :data}]
         (into
          prev-executions
          (map #(assoc % :id (random-uuid)))
          executions)))
      :merge-executions
      (xs/xform-ctx-from-evt
       {:ctx-prop :executions}
       (fn [prev-executions {{:keys [executions]} :data}]
         (let [new-by-id (group-by :id executions)]
           (into
            []
            (map
             (fn [{:keys [id] :as e}]
               (merge e (get-in new-by-id [id 0]))))
            prev-executions))))
      :update-cols
      (xs/xform-ctx-from-evt
       {:ctx-prop :cols}
       (fn [prev-cols {{:keys [executions]} :data}]
         (reduce
          (fn [{:keys [inputs outputs]} {{exec-results :results} :res {exec-inputs :inputs} :req}]
            {:inputs (merge-cols inputs exec-inputs)
             :outputs (merge-cols outputs exec-results)})
          prev-cols
          executions)))
      :store-next-req
      (xs/xform-ctx-from-evt
       {:ctx-prop :next-req}
       (fn [_ {{:keys [next-token]} :data}]
         (when next-token
           {:token next-token})))
      :store-error
      (xs/assign-ctx-from-evt
       {:ctx-prop :error
        :evt-prop :data})}
     :services
     {:load-executions (fn [{:keys [next-req] {:keys [name execution-id]} :params :as ctx}]
                         (remote-pkgs/load-execution-set-executions name execution-id next-req))
      :pointer-loading-machine pointer-loading-machine}}}))

(defn svc
  ([]
   (svc nil))
  ([opts]
   (xs/interpret-and-start machine opts)))
