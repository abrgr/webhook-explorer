(ns webhook-explorer.specs.xstate
  (:require [clojure.spec.alpha :as s]))

(s/def :xstate/transition-to
  (s/cat :to-glyph #{'->}
         :target keyword?
         :mods (s/* (s/alt :action (s/cat :action-glyph #{'!}
                                          :action keyword?)
                           :guard (s/cat :guard-glyph #{'|}
                                         :guard keyword?)))))

(s/def :xstate/transition
  (s/coll-of
   (s/cat :event keyword?
          :to :xstate/transition-to)
   :min-count 1
   :kind vector?))

(s/def :xstate/delayed-transition
  (s/coll-of
   (s/cat :delay-glyph #{'after}
          :delay-ms pos-int?
          :to :xstate/transition-to)
   :min-count 1
   :kind vector?))

(s/def :xstate/invocation
  (s/coll-of
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
                                                  :data map?))))
   :min-count 1
   :kind vector?))

(s/def :xstate/entry-actions
  (s/coll-of
   (s/cat :entry-glyph #{'>!}
          :action (s/+ keyword?))
   :kind vector?
   :count 1))

(s/def :xstate/exit-actions
  (s/coll-of
   (s/cat :exit-glyph #{'!>}
          :action (s/+ keyword?))
   :kind vector?
   :count 1))

(s/def :xstate/activities
  (s/coll-of
   (s/cat :activity-glyph #{'!+}
          :activity-names (s/+ keyword?))
   :min-count 1
   :kind vector?))

(s/def :xstate/state-def
  (s/+
   (s/alt :transition :xstate/transition
          :delayed-transition :xstate/delayed-transition
          :invocation :xstate/invocation
          :entry-actions :xstate/entry-actions
          :exit-actions :xstate/exit-actions
          :activities :xstate/activities)))

(s/def :xstate/state
  (s/cat :id keyword?
         :def :xstate/state-def))

(s/def :xstate/any-state
  ; TODO: how do i specify that this is a refinement of :xstate/state?
  (s/cat :id #{'*}
         :def (s/+ (s/alt :transition :xstate/transition))))

(s/def :xstate/config
  (s/spec
   (s/cat :parallel (s/? #{'||})
          :any-state (s/? :xstate/any-state)
          :init-state (s/cat :ornament #{'>}
                             :state :xstate/state)
          :unadorned-states (s/* (s/cat :state :xstate/state))
          :final-states (s/* (s/cat :ornament #{'x}
                                    :state :xstate/state)))))

(s/def :xstate-js/target string?)
(s/def :xstate-js/cond string?)
(s/def :xstate-js/actions
  (s/coll-of string?))
(s/def :xstate-js/transition
  (s/keys
   :req-un [:xstate-js/target]
   :opt-un [:xstate-js/cond
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
            :xstate-js/activities]))
(s/def :xstate-js/id string?)
(s/def :xstate-js/initial string?)
(s/def :xstate-js/after
  (s/map-of
   pos-int?
   (s/or :single :xstate-js/transition
         :many (s/coll-of :xstate-js/transition))))
(s/def :xstate-js/on
  (s/map-of
   keyword?
   (s/or :single :xstate-js/transition
         :many (s/coll-of :xstate-js/transition))))
(s/def :xstate-js/states
  (s/map-of
   keyword?
   :xstate-js/state))
(s/def :xstate-js/machine
  (s/keys
   :req-un [:xstate-js/id
            :xstate-js/initial
            :xstate-js/states]
   :opt-un [:xstate-js/on]))
