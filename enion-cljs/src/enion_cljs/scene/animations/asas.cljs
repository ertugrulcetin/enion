(ns enion-cljs.scene.animations.asas
  (:require
    [enion-cljs.scene.animations.core :as anim :refer [model-entity]]
    [enion-cljs.scene.keyboard :as k]
    [enion-cljs.scene.pc :as pc]
    [enion-cljs.scene.utils :as utils]))

;; TODO define combo rand ranges in a var
(def events
  (concat
    anim/common-states
    [{:anim-state "attackDagger" :event "onAttackDaggerEnd" :skill? true :end? true}
     {:anim-state "attackDagger" :event "onAttackDaggerCall" :call? true}
     {:anim-state "attackDagger" :event "onAttackDaggerLock" :r-lock? true}
     {:anim-state "attackDagger" :event "onAttackDaggerLockRelease" :r-release? true}
     {:anim-state "attackR" :event "onAttackREnd" :skill? true :end? true}
     {:anim-state "attackR" :event "onAttackRCall" :call? true}
     {:anim-state "attackR" :event "onAttackRLock" :r-lock? true}
     {:anim-state "attackR" :event "onAttackRLockRelease" :r-release? true}
     {:anim-state "hide" :event "onHideCall" :call? true}
     {:anim-state "hide" :event "onHideEnd" :skill? true :end? true}]))

(def last-one-hand-combo (atom (js/Date.now)))

;; TODO can't jump while attacking - collision wise
(defn process-skills [e state]
  (when-not (-> e .-event .-repeat)
    (let [active-state (pc/get-anim-state model-entity)]
      (when (k/pressing-wasd?)
        (swap! state assoc :target-pos-available? false)
        (cond
          (anim/skill-cancelled? "attackDagger" active-state state)
          (anim/cancel-skill "attackDagger")

          (anim/skill-cancelled? "attackR" active-state state)
          (anim/cancel-skill "attackR")

          (anim/skill-cancelled? "hide" active-state state)
          (anim/cancel-skill "hide")))
      (cond
        (and (= active-state "attackDagger")
             (anim/skill-pressed? e "attackR")
             (:can-r-attack-interrupt? @state))
        (do
          (println "R combo!")
          (pc/set-anim-boolean model-entity "attackDagger" false)
          (pc/set-anim-boolean model-entity "attackR" true))

        (and (= active-state "attackR")
             (anim/skill-pressed? e "attackDagger")
             (:can-r-attack-interrupt? @state)
             (> (- (js/Date.now) @last-one-hand-combo) (utils/rand-between 750 1200)))
        (do
          (println "dagger combo...!")
          (pc/set-anim-boolean model-entity "attackR" false)
          (pc/set-anim-boolean model-entity "attackDagger" true)
          (reset! last-one-hand-combo (js/Date.now)))

        (and (= "idle" active-state) (k/pressing-wasd?))
        (pc/set-anim-boolean model-entity "run" true)

        (and (anim/idle-run-states active-state) (pc/key? e :KEY_SPACE) (:on-ground? @state))
        (pc/set-anim-boolean model-entity "jump" true)

        (and (anim/idle-run-states active-state) (anim/skill-pressed? e "attackDagger"))
        (pc/set-anim-boolean model-entity "attackDagger" true)

        (and (anim/idle-run-states active-state) (anim/skill-pressed? e "hide"))
        (pc/set-anim-boolean model-entity "hide" true)

        (and (anim/idle-run-states active-state) (anim/skill-pressed? e "attackR"))
        (pc/set-anim-boolean model-entity "attackR" true)))))

(comment
  (do
    (doseq [{:keys [event]} events]
      (pc/off-anim enion-cljs.scene.entities.player/model-entity event))
    (anim/register-anim-events enion-cljs.scene.entities.player/state
      enion-cljs.scene.entities.player/player-entity))

  (:skill-locked? @enion-cljs.scene.entities.player/state)
  (pc/get-anim-state model-entity)
  )
