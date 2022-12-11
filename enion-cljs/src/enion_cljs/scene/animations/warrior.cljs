(ns enion-cljs.scene.animations.warrior
  (:require
    [enion-cljs.scene.animations.core :as anim :refer [model-entity]]
    [enion-cljs.scene.keyboard :as k]
    [enion-cljs.scene.pc :as pc]
    [enion-cljs.scene.utils :as utils]))

;; TODO current state leftover in idle mode but was able to run at the same time
(def events
  (concat
    anim/common-states
    [{:anim-state "attackOneHand" :event "onAttackOneHandEnd" :skill? true :end? true}
     {:anim-state "attackOneHand" :event "onAttackOneHandCall" :call? true}
     {:anim-state "attackOneHand" :event "onAttackOneHandLockRelease" :r-release? true}
     {:anim-state "attackOneHand" :event "onAttackOneHandLock" :r-lock? true}
     {:anim-state "attackR" :event "onAttackREnd" :skill? true :end? true}
     {:anim-state "attackR" :event "onAttackRCall" :call? true}
     {:anim-state "attackR" :event "onAttackRLockRelease" :r-release? true}
     {:anim-state "attackR" :event "onAttackRLock" :r-lock? true}
     {:anim-state "attackSlowDown" :event "onAttackSlowDownCall" :call? true}
     {:anim-state "attackSlowDown" :event "onAttackSlowDownEnd" :skill? true :end? true}]))

(def last-one-hand-combo (atom (js/Date.now)))

;; TODO can't jump while attacking - collision wise
(defn process-skills [e state]
  (when-not (-> e .-event .-repeat)
    (let [active-state (pc/get-anim-state model-entity)]
      (when (k/pressing-wasd?)
        (swap! state assoc :target-pos-available? false)
        (cond
          (anim/skill-cancelled? "attackOneHand" active-state state)
          (anim/cancel-skill "attackOneHand")

          (anim/skill-cancelled? "attackR" active-state state)
          (anim/cancel-skill "attackR")

          (anim/skill-cancelled? "attackSlowDown" active-state state)
          (anim/cancel-skill "attackSlowDown")))
      (cond
        (and (= active-state "attackOneHand")
             (anim/skill-pressed? e "attackR")
             (:can-r-attack-interrupt? @state))
        (do
          (println "R combo!")
          (pc/set-anim-boolean model-entity "attackOneHand" false)
          (pc/set-anim-boolean model-entity "attackR" true))

        (and (= active-state "attackR")
             (anim/skill-pressed? e "attackOneHand")
             (:can-r-attack-interrupt? @state)
             (> (- (js/Date.now) @last-one-hand-combo) (utils/rand-between 750 1200)))
        (do
          (println "one hand combo...!")
          (pc/set-anim-boolean model-entity "attackR" false)
          (pc/set-anim-boolean model-entity "attackOneHand" true)
          (reset! last-one-hand-combo (js/Date.now)))

        (and (= "idle" active-state) (k/pressing-wasd?))
        (pc/set-anim-boolean model-entity "run" true)

        (and (anim/idle-run-states active-state) (pc/key? e :KEY_SPACE))
        (pc/set-anim-boolean model-entity "jump" true)

        (and (anim/idle-run-states active-state) (anim/skill-pressed? e "attackOneHand"))
        (pc/set-anim-boolean model-entity "attackOneHand" true)

        (and (anim/idle-run-states active-state) (anim/skill-pressed? e "attackSlowDown"))
        (pc/set-anim-boolean model-entity "attackSlowDown" true)

        (and (anim/idle-run-states active-state) (anim/skill-pressed? e "attackR"))
        (pc/set-anim-boolean model-entity "attackR" true)))))

(comment
  (do
    (doseq [{:keys [event]} events]
      (pc/off-anim enion-cljs.scene.entities.player/model-entity event))
    (anim/register-anim-events enion-cljs.scene.entities.player/state))

  (:skill-locked? @enion-cljs.scene.entities.player/state)
  )
