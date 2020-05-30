(ns webhook-explorer.specs.xstate
  (:require [clojure.spec.alpha :as s]
            [goog.object :as obj]))

(quote a/b)
(s/def :xstate/transition-to
  (s/cat :to-glyph #{'->}
         :target (s/alt :self #{'*self*}
                        :ext-self #{(symbol "*ext*" "*self*")} ; TODO: why does the compiler complain when using '*ext*/*self*? Still tries to look up the namespace even when quoted
                        :other keyword?)
         :mods (s/* (s/alt :action (s/cat :action-glyph #{'!}
                                          :action keyword?)
                           :guard (s/cat :guard-glyph #{'|}
                                         :guard keyword?)))))
(s/def :xstate/transition
  (s/spec
   (s/cat :event (s/alt :real keyword? :transient #{'*transient*})
          :to :xstate/transition-to)))
(s/def :xstate/delayed-transition
  (s/spec
   (s/cat :delay-glyph #{'after}
          :delay-ms pos-int?
          :to :xstate/transition-to)))
(s/def :xstate/invocation
  (s/spec
   (s/cat :invocation-glyph #{'$}
          :service-name keyword?
          :handlers (s/alt :promise (s/cat
                                     :on-done (s/cat :on-done-glyph #{:on-done}
                                                     :to :xstate/transition-to)
                                     :on-error (s/cat :on-done-glyph #{:on-error}
                                                      :to :xstate/transition-to))
                           :machine (s/cat
                                     :on-done (s/cat :on-done-glyph #{:on-done}
                                                     :to :xstate/transition-to)
                                     :data (s/cat :data-glyph #{:data}
                                                  :data map?))))))
(s/def :xstate/entry-actions
  (s/spec
   (s/cat :entry-glyph #{'>!}
          :action (s/+ keyword?))))
(s/def :xstate/exit-actions
  (s/spec
   (s/cat :exit-glyph #{'!>}
          :action (s/+ keyword?))))
(s/def :xstate/activities
  (s/spec
   (s/cat :activity-glyph #{'!+}
          :activity-names (s/+ keyword?))))
(s/def :xstate/child-states
  (s/spec
   (s/cat :children #{'children}
          :cfg :xstate/config)))
(s/def :xstate/extra-cfg
  (s/cat
   :key keyword?
   :value (s/with-gen any? #(s/gen int?)))) ; adding a gen just to speed things up a bit
(s/def :xstate/state-def
  (s/spec
   (s/*
    (s/alt :transition :xstate/transition
           :delayed-transition :xstate/delayed-transition
           :invocation :xstate/invocation
           :entry-actions :xstate/entry-actions
           :exit-actions :xstate/exit-actions
           :activities :xstate/activities
           :child-states :xstate/child-states
           :extra-cfg :xstate/extra-cfg))))
(s/def :xstate/state
  (s/cat :id keyword?
         :def :xstate/state-def))
(s/def :xstate/any-state
  ; TODO: how do i specify that this is a refinement of :xstate/state?
  (s/cat :id #{'*}
         :def (s/spec (s/+ (s/alt :transition :xstate/transition)))))
(s/def :xstate/config
  (s/cat :parallel (s/? #{'||})
         :any-state (s/? :xstate/any-state)
         :init-state (s/? (s/cat :ornament #{'>}
                                 :state :xstate/state))
         :unadorned-states (s/* (s/cat :state :xstate/state))
         :final-states (s/* (s/cat :ornament #{'x}
                                   :state :xstate/state))))
(s/def :xstate/m (partial instance? js/Object))
(s/def :xstate/machine
  (s/keys
   :req-un [:xstate/m]))
(s/def :xstate.runtime-config/actions
  (s/map-of
   keyword?
   (s/and (partial instance? js/Object)
          #(obj/containsKey % "type"))))
(s/def :xstate.runtime-config.guard/cond map?)
(s/def :xstate.runtime-config.guard/state map?)
(s/def :xstate.runtime-config/guards
  (s/map-of
   keyword?
   (s/fspec
    :args (s/cat :ctx any?
                 :evt map?
                 :meta (s/keys :req-un [:xstate.runtime-config.guard/cond
                                        :xstate.runtime-config.guard/state]))
    :ret boolean?)))

(s/def :xstate.runtime-config/activities
  (s/map-of
   keyword?
   (s/fspec
    :args (s/cat)
    :ret (s/fspec :args (s/cat) :ret nil?))))
(s/def :xstate.runtime-config/services
  (s/or
   :machine :xstate/machine
   :fn (s/fspec
        :args (s/cat :ctx any? :evt map?)
        :ret (s/or :promise (partial instance? js/Promise)
                   :callback (s/fspec
                              :args (s/cat :send fn? :recv fn?)
                              :ret fn?)))))
(s/def :xstate/runtime-config
  (s/keys
   :opt-un [:xstate.runtime-config/actions
            :xstate.runtime-config/guards
            :xstate.runtime-config/activities
            :xstate.runtime-config/services]))

(s/def :xstate-js/target string?)
(s/def :xstate-js/cond string?)
(s/def :xstate-js/actions
  (s/coll-of string?))
(s/def :xstate-js/transition
  (s/keys
   :opt-un [:xstate-js/target
            :xstate-js/cond
            :xstate-js/actions]))
(s/def :xstate-js/src string?)
(s/def :xstate-js/invocation
  (s/keys
   :req-un [:xstate-js/id
            :xstate-js/src]))
(s/def :xstate-js/invoke
  (s/or :single :xstate-js/invocation
        :many (s/coll-of :xstate-js/invocation)))
(s/def :xstate-js/entry :xstate-js/actions)
(s/def :xstate-js/exit :xstate-js/actions)
(s/def :xstate-js/activities
  (s/coll-of string?))
(s/def :xstate-js/state
  (s/keys
   :opt-un [:xstate-js/on
            :xstate-js/after
            :xstate-js/invoke
            :xstate-js/entry
            :xstate-js/exit
            :xstate-js/activities
            :xstate-js/states]))
(s/def :xstate-js/id string?)
(s/def :xstate-js/initial string?)
(s/def :xstate-js/after
  (s/map-of
   pos-int?
   (s/or :single :xstate-js/transition
         :many (s/coll-of :xstate-js/transition))))
(s/def :xstate-js/on
  (s/map-of
   (s/or :real keyword? :transient #{""})
   (s/or :single :xstate-js/transition
         :many (s/coll-of :xstate-js/transition))))
(s/def :xstate-js/states
  (s/map-of
   keyword?
   :xstate-js/state))
(s/def :xstate-js/type #{"parallel" "final"})
(s/def :xstate-js/machine
  (s/keys
   :req-un [:xstate-js/id
            :xstate-js/states]
   :opt-un [:xstate-js/initial
            :xstate-js/on
            :xstate-js/type]))

(s/def :xstate-test/init
  s/spec?)
(s/def :xstate-test/spec s/spec?)
(s/def :xstate-test/states
  (s/map-of
   keyword?
   (s/or :spec s/spec?
         :state (s/and :xstate-test/states
                       (s/keys :opt [:xstate-test/spec])))))
(s/def :xstate-test/ctx-specs
  (s/keys
   :opt-un [:xstate-test/init
            :xstate-test/states]))
(s/def :xstate-test/ctx any?)
(s/def :xstate-test/ctx-spec s/spec?)
(s/def :xstate-test/cfg
  (s/and
   :xstate/runtime-config
   (s/keys
    :req-un [:xstate/machine
             :xstate-test/ctx-specs
             :xstate-test/ctx-spec
             :xstate-test/ctx])))
