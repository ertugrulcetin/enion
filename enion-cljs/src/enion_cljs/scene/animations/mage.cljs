(ns enion-cljs.scene.animations.mage
  (:require
    [applied-science.js-interop :as j]
    [enion-cljs.scene.animations.core :as anim :refer [model-entity]]
    [enion-cljs.scene.keyboard :as k]
    [enion-cljs.scene.pc :as pc])
  (:require-macros
    [enion-cljs.scene.macros :as m]))

;; TODO define combo rand ranges in a var
(def events
  (concat
    anim/common-states
    [{:anim-state "attackRange" :event "onAttackRangeEnd" :skill? true :end? true}
     {:anim-state "attackRange" :event "onAttackRangeCall" :call? true}
     {:anim-state "attackSingle" :event "onAttackSingleEnd" :skill? true :end? true}
     {:anim-state "attackSingle" :event "onAttackSingleCall" :call? true}
     {:anim-state "attackR" :event "onAttackREnd" :skill? true :end? true}
     {:anim-state "attackR" :event "onAttackRCall" :call? true}
     {:anim-state "teleport" :event "onTeleportCall" :call? true}
     {:anim-state "teleport" :event "onTeleportEnd" :skill? true :end? true}]))

;; TODO can't jump while attacking - collision wise
(defn process-skills [e state]
  (when-not (-> e .-event .-repeat)
    (let [active-state (pc/get-anim-state model-entity)]
      (m/process-cancellable-skills ["attackRange" "attackSingle" "attackR" "teleport"] active-state state)
      (cond
        (and (= "idle" active-state) (k/pressing-wasd?))
        (pc/set-anim-boolean model-entity "run" true)

        (and (anim/idle-run-states active-state) (pc/key? e :KEY_SPACE) (j/get state :on-ground?))
        (pc/set-anim-boolean model-entity "jump" true)

        (and (anim/idle-run-states active-state) (anim/skill-pressed? e "attackRange"))
        (pc/set-anim-boolean model-entity "attackRange" true)

        (and (anim/idle-run-states active-state) (anim/skill-pressed? e "attackSingle"))
        (pc/set-anim-boolean model-entity "attackSingle" true)

        (and (anim/idle-run-states active-state) (anim/skill-pressed? e "teleport"))
        (pc/set-anim-boolean model-entity "teleport" true)

        (and (anim/idle-run-states active-state) (anim/skill-pressed? e "attackR"))
        (pc/set-anim-boolean model-entity "attackR" true)))))
