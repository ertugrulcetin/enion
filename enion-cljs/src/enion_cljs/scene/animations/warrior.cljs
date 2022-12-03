(ns enion-cljs.scene.animations.warrior
  (:require
    [enion-cljs.scene.animations.core :refer [model-entity]]
    [enion-cljs.scene.keyboard :as k]
    [enion-cljs.scene.pc :as pc]))

(def events
  [{:anim-state "idle" :event "onIdleStart" :idle-or-run-start? true}
   {:anim-state "run" :event "onRunStart" :idle-or-run-start? true}
   {:anim-state "jump" :event "onJumpEnd" :end? true}
   {:anim-state "attackOneHand" :event "onAttackOneHandEnd" :attack? true :end? true}
   {:anim-state "attackOneHand" :event "onAttackOneHandCall" :call? true}
   {:anim-state "attackOneHand" :event "onAttackOneHandLockRelease" :r-release? true}
   {:anim-state "attackOneHand" :event "onAttackOneHandLock" :r-lock? true}
   {:anim-state "attackR" :event "onAttackREnd" :attack? true :end? true}
   {:anim-state "attackR" :event "onAttackRCall" :call? true}
   {:anim-state "attackR" :event "onAttackRLockRelease" :r-release? true}
   {:anim-state "attackR" :event "onAttackRLock" :r-lock? true}
   {:anim-state "attackSlowDown" :event "onAttackSlowDownCall" :call? true}
   {:anim-state "attackSlowDown" :event "onAttackSlowDownEnd" :attack? true :end? true}])

(def idle-run-states #{"idle" "run"})

(defn- skill-cancelled? [anim-state active-state state]
  (and (= active-state anim-state)
       (not (:skill-locked? @state))
       (not (k/pressing-attacks?))))

(defn- cancel-skill [anim-state]
  (pc/set-anim-boolean model-entity anim-state false)
  (pc/set-anim-boolean model-entity "run" true))

;; TODO can't jump while attacking
(defn- process-skills [e state]
  (when-not (-> e .-event .-repeat)
    (let [active-state (pc/get-anim-state model-entity)]
      (when (k/pressing-wasd?)
        (swap! state assoc :target-pos-available? false)
        (cond
          (skill-cancelled? "attackOneHand" active-state state)
          (cancel-skill "attackOneHand")

          (skill-cancelled? "attackR" active-state state)
          (cancel-skill "attackR")

          (skill-cancelled? "attackSlowDown" active-state state)
          (cancel-skill "attackSlowDown")))
      (cond
        (and (= active-state "attackOneHand")
             (pc/key? e :KEY_R)
             (:can-r-attack-interrupt? @state))
        (do
          (println "BETWEEN COMBO!!")
          (pc/set-anim-boolean model-entity "attackOneHand" false)
          (pc/set-anim-boolean model-entity "attackR" true))

        (and (= active-state "attackR")
             (pc/key? e :KEY_1)
             (:can-r-attack-interrupt? @state))
        (do
          (println "RRR BETWEEN COMBO!!")
          (pc/set-anim-boolean model-entity "attackR" false)
          (pc/set-anim-boolean model-entity "attackOneHand" true))

        (and (= "idle" active-state)
             (k/pressing-wasd?))
        (pc/set-anim-boolean model-entity "run" true)

        (and (idle-run-states active-state)
             (pc/key? e :KEY_SPACE))
        (pc/set-anim-boolean model-entity "jump" true)

        (and (idle-run-states active-state)
             (pc/key? e :KEY_1))
        (pc/set-anim-boolean model-entity "attackOneHand" true)

        (and (idle-run-states active-state)
             (pc/key? e :KEY_2))
        (pc/set-anim-boolean model-entity "attackSlowDown" true)

        (and (idle-run-states active-state)
             (pc/key? e :KEY_R))
        (pc/set-anim-boolean model-entity "attackR" true)))))

(defn register-anim-events [state model-entity pressing-wasd-or-has-target?]
  (doseq [{:keys [anim-state
                  event
                  attack?
                  call?
                  end?
                  r-lock?
                  r-release?
                  idle-or-run-start?]} events]
    (pc/on-anim model-entity event
                (fn []
                  (when end?
                    (pc/set-anim-boolean model-entity anim-state false)
                    (when (pressing-wasd-or-has-target?)
                      (pc/set-anim-boolean model-entity "run" true))
                    (when attack?
                      (swap! state assoc
                             :skill-locked? false
                             :can-r-attack-interrupt? false)))
                  (cond
                    call? (swap! state assoc :skill-locked? true)
                    r-release? (swap! state assoc :can-r-attack-interrupt? true)
                    r-lock? (swap! state assoc :can-r-attack-interrupt? false)
                    idle-or-run-start? (swap! state assoc :skill-locked? false))))))

(comment
  (doseq [{:keys [event]} events]
    (pc/off-anim enion-cljs.scene.entities.player/model-entity event))

  (register-anim-events
    enion-cljs.scene.entities.player/state
    enion-cljs.scene.entities.player/model-entity
    enion-cljs.scene.entities.player/pressing-wasd-or-has-target?)
  )
