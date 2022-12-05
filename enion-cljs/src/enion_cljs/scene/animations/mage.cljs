(ns enion-cljs.scene.animations.mage
  (:require
    [enion-cljs.scene.animations.core :as anim :refer [model-entity]]
    [enion-cljs.scene.keyboard :as k]
    [enion-cljs.scene.pc :as pc]
    [enion-cljs.scene.utils :as utils]))

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
      (when (k/pressing-wasd?)
        (swap! state assoc :target-pos-available? false)
        (cond
          (anim/skill-cancelled? "attackRange" active-state state)
          (anim/cancel-skill "attackRange")

          (anim/skill-cancelled? "attackSingle" active-state state)
          (anim/cancel-skill "attackSingle")

          (anim/skill-cancelled? "attackR" active-state state)
          (anim/cancel-skill "attackR")

          (anim/skill-cancelled? "teleport" active-state state)
          (anim/cancel-skill "teleport")))
      (cond
        (and (= "idle" active-state) (k/pressing-wasd?))
        (pc/set-anim-boolean model-entity "run" true)

        (and (anim/idle-run-states active-state) (pc/key? e :KEY_SPACE))
        (pc/set-anim-boolean model-entity "jump" true)

        (and (anim/idle-run-states active-state) (anim/skill-pressed? e "attackRange"))
        (pc/set-anim-boolean model-entity "attackRange" true)

        (and (anim/idle-run-states active-state) (anim/skill-pressed? e "attackSingle"))
        (pc/set-anim-boolean model-entity "attackSingle" true)

        (and (anim/idle-run-states active-state) (anim/skill-pressed? e "teleport"))
        (pc/set-anim-boolean model-entity "teleport" true)

        (and (anim/idle-run-states active-state) (anim/skill-pressed? e "attackR"))
        (pc/set-anim-boolean model-entity "attackR" true)))))

(comment
  (do
    (doseq [{:keys [event]} events]
      (pc/off-anim enion-cljs.scene.entities.player/model-entity event))
    (anim/register-anim-events enion-cljs.scene.entities.player/state))

  (:skill-locked? @enion-cljs.scene.entities.player/state)
  )
