(ns enion-cljs.scene.animations.priest
  (:require
    [applied-science.js-interop :as j]
    [enion-cljs.scene.animations.core :as anim :refer [model-entity]]
    [enion-cljs.scene.keyboard :as k]
    [enion-cljs.scene.pc :as pc]
    [enion-cljs.scene.utils :as utils]))

(def events
  (concat anim/common-states
          [{:anim-state "breakDefense" :event "onBreakDefenseEnd" :skill? true :end? true}
           {:anim-state "breakDefense" :event "onBreakDefenseCall" :call? true}
           {:anim-state "heal" :event "onHealEnd" :skill? true :end? true}
           {:anim-state "heal" :event "onHealCall" :call? true}
           {:anim-state "attackR" :event "onAttackREnd" :skill? true :end? true}
           {:anim-state "attackR" :event "onAttackRCall" :call? true}
           {:anim-state "cure" :event "onCureCall" :call? true}
           {:anim-state "cure" :event "onCureEnd" :skill? true :end? true}]))

;; TODO can't jump while attacking - collision wise
(defn process-skills [e state]
  (when-not (j/get-in e [:event :repeat])
    (let [active-state (pc/get-anim-state model-entity)]
      (when (k/pressing-wasd?)
        ;; TODO maybe we need to delete this
        (pc/set-anim-boolean model-entity "run" true)
        (j/assoc! state :target-pos-available? false)
        (cond
          (anim/skill-cancelled? "breakDefense" active-state state)
          (anim/cancel-skill "breakDefense")

          (anim/skill-cancelled? "heal" active-state state)
          (anim/cancel-skill "heal")

          (anim/skill-cancelled? "attackR" active-state state)
          (anim/cancel-skill "attackR")

          (anim/skill-cancelled? "cure" active-state state)
          (anim/cancel-skill "cure")))
      (cond
        (and (= "idle" active-state) (k/pressing-wasd?))
        (pc/set-anim-boolean model-entity "run" true)

        (and (anim/idle-run-states active-state) (pc/key? e :KEY_SPACE) (j/get state :on-ground?))
        (pc/set-anim-boolean model-entity "jump" true)

        (and (anim/idle-run-states active-state) (anim/skill-pressed? e "breakDefense"))
        (pc/set-anim-boolean model-entity "breakDefense" true)

        (and (anim/idle-run-states active-state) (anim/skill-pressed? e "heal"))
        (pc/set-anim-boolean model-entity "heal" true)

        (and (anim/idle-run-states active-state) (anim/skill-pressed? e "cure"))
        (pc/set-anim-boolean model-entity "cure" true)

        (and (anim/idle-run-states active-state) (anim/skill-pressed? e "attackR"))
        (pc/set-anim-boolean model-entity "attackR" true)))))
