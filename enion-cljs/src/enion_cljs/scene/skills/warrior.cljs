(ns enion-cljs.scene.skills.warrior
  (:require
    [applied-science.js-interop :as j]
    [enion-cljs.common :refer [fire on]]
    [enion-cljs.scene.keyboard :as k]
    [enion-cljs.scene.pc :as pc]
    [enion-cljs.scene.simulation :as sm]
    [enion-cljs.scene.skills.core :as skills]
    [enion-cljs.scene.skills.effects :as skills.effects]
    [enion-cljs.scene.states :as st :refer [player]]
    [enion-cljs.scene.utils :as utils])
  (:require-macros
    [enion-cljs.scene.macros :as m]))

;; TODO current state leftover in idle mode but was able to run at the same time
(def events
  (concat
    skills/common-states
    [{:anim-state "attackOneHand" :event "onAttackOneHandEnd" :skill? true :end? true}
     {:anim-state "attackOneHand" :event "onAttackOneHandCall" :call? true :call-name :attack-one-hand}
     {:anim-state "attackOneHand" :event "onAttackOneHandLockRelease" :r-release? true}
     {:anim-state "attackOneHand" :event "onAttackOneHandLock" :r-lock? true}
     {:anim-state "attackR" :event "onAttackREnd" :skill? true :end? true}
     {:anim-state "attackR" :event "onAttackRCall" :call? true}
     {:anim-state "attackR" :event "onAttackRLockRelease" :r-release? true}
     {:anim-state "attackR" :event "onAttackRLock" :r-lock? true}
     {:anim-state "attackSlowDown" :event "onAttackSlowDownCall" :call? true :call-name :attack-slow-down}
     {:anim-state "attackSlowDown" :event "onAttackSlowDownEnd" :skill? true :end? true}]))

(def last-one-hand-combo (atom (js/Date.now)))

(def close-attack-distance-threshold 0.75)

(defn- attack-one-hand [player-id]
  (let [enemy (st/get-other-player player-id)
        enemy-model-entity (st/get-model-entity player-id)
        ping 100
        latency (* 2 ping)]
    (pc/set-anim-boolean (st/get-model-entity) "attackOneHand" true)
    #_(js/setTimeout
        (fn []
          (skills.effects/apply-effect-attack-one-hand enemy)
          (when (> (rand-int 10) 8)
            (pc/set-anim-int enemy-model-entity "health" 0)
            (st/disable-player-collision player-id)
            (st/set-health player-id 0)))
        latency)))

(defn- attack-slow-down [player-id]
  (let [enemy (st/get-other-player player-id)
        enemy-model-entity (st/get-model-entity player-id)
        ping 100
        latency (* 2 ping)]
    (pc/set-anim-boolean (st/get-model-entity) "attackSlowDown" true)
    #_(js/setTimeout
        (fn []
          (skills.effects/apply-effect-attack-slow-down enemy)
          (when (> (rand-int 10) 8)
            (pc/set-anim-int enemy-model-entity "health" 0)
            (st/disable-player-collision player-id)
            (st/set-health player-id 0)))
        latency)))

(on :attack-one-hand
    (fn []
      (fire :ui-cooldown "attackOneHand")
      (let [player-id (st/get-selected-player-id)
            enemy (st/get-other-player player-id)]
        (skills.effects/apply-effect-attack-one-hand enemy)
        (if (> (rand-int 10) 8)
          (do
            (pc/set-anim-int (st/get-model-entity player-id) "health" 0)
            (st/disable-player-collision player-id)
            (st/set-health player-id 0))
          (st/set-health player-id (rand-int 100))))))

(on :attack-slow-down
    (fn []
      (fire :ui-cooldown "attackSlowDown")
      (let [player-id (st/get-selected-player-id)
            enemy (st/get-other-player player-id)]
        (skills.effects/apply-effect-attack-slow-down enemy)
        (when (> (rand-int 10) 8)
          (pc/set-anim-int (st/get-model-entity player-id) "health" 0)
          (st/disable-player-collision player-id)
          (st/set-health player-id 0)))))

(comment
  (sm/spawn 1)
  (sm/spawn-all)
  )

(defn process-skills [e]
  ;; TODO add check if our char is alive
  (when-not (-> e .-event .-repeat)
    (let [model-entity (st/get-model-entity)
          active-state (pc/get-anim-state model-entity)
          selected-player-id (st/get-selected-player-id)]
      (m/process-cancellable-skills ["attackOneHand" "attackSlowDown" "attackR"] (j/get-in e [:event :code]) active-state player)
      (cond
        (and (= active-state "attackOneHand")
             (skills/skill-pressed? e "attackR")
             (j/get player :can-r-attack-interrupt?))
        (do
          (println "R combo!")
          (pc/set-anim-boolean model-entity "attackOneHand" false)
          (pc/set-anim-boolean model-entity "attackR" true))

        (and (= active-state "attackR")
             (skills/skill-pressed? e "attackOneHand")
             (j/get player :can-r-attack-interrupt?)
             (> (- (js/Date.now) @last-one-hand-combo) (utils/rand-between 750 1200)))
        (do
          (println "one hand combo...!")
          (pc/set-anim-boolean model-entity "attackR" false)
          (pc/set-anim-boolean model-entity "attackOneHand" true)
          (reset! last-one-hand-combo (js/Date.now)))

        (and (= "idle" active-state) (k/pressing-wasd?))
        (pc/set-anim-boolean model-entity "run" true)

        (and (skills/idle-run-states active-state) (pc/key? e :KEY_SPACE) (j/get player :on-ground?))
        (pc/set-anim-boolean model-entity "jump" true)

        ;; TODO check enough mana?
        (and
          (skills/idle-run-states active-state)
          (skills/skill-pressed? e "attackOneHand")
          (st/cooldown-ready? "attackOneHand")
          (st/enemy-selected? selected-player-id)
          (st/alive? selected-player-id)
          (<= (st/distance-to selected-player-id) close-attack-distance-threshold))
        (attack-one-hand selected-player-id)

        (and
          (skills/idle-run-states active-state)
          (skills/skill-pressed? e "attackSlowDown")
          (st/cooldown-ready? "attackSlowDown")
          (st/enemy-selected? selected-player-id)
          (st/alive? selected-player-id)
          (<= (st/distance-to selected-player-id) close-attack-distance-threshold))
        (attack-slow-down selected-player-id)

        (and
          (skills/idle-run-states active-state)
          (skills/skill-pressed? e "attackR")
          (st/enemy-selected? selected-player-id)
          (st/alive? selected-player-id)
          (<= (st/distance-to selected-player-id) close-attack-distance-threshold))
        (pc/set-anim-boolean model-entity "attackR" true)

        (and
          (skills/skill-pressed? e "shieldWall")
          (st/cooldown-ready? "shieldWall"))
        (do
          (println "Shieldwall")
          (fire :ui-cooldown "shieldWall")
          (skills.effects/apply-effect-shield-wall player))

        (and
          (skills/skill-pressed? e "fleetFoot")
          (st/cooldown-ready? "fleetFoot"))
        (do
          (println "fleetFoot")
          (fire :ui-cooldown "fleetFoot")
          (skills.effects/apply-effect-shield-wall player))))))

(comment
  player
  (fire :re-init)
  (skills.effects/apply-effect-got-defense-break player)
  (skills.effects/apply-effect-hp-potion player)
  (skills.effects/apply-effect-mp-potion player)
  (skills.effects/apply-effect-got-cure player)
  (skills.effects/apply-effect-fleet-foot player)
  (j/get-in player [:effects :particle_defense_break_hands])
  )
