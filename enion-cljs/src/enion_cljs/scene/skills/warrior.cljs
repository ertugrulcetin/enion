(ns enion-cljs.scene.skills.warrior
  (:require
    [applied-science.js-interop :as j]
    [enion-cljs.common :as common :refer [fire on]]
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
;; TODO  move attackR to common-states
(def events
  (concat
    skills/common-states
    [{:anim-state "attackOneHand" :event "onAttackOneHandEnd" :skill? true :end? true}
     {:anim-state "attackOneHand" :event "onAttackOneHandCall" :call? true :call-name :attack-one-hand}
     {:anim-state "attackOneHand" :event "onAttackOneHandLockRelease" :r-release? true}
     {:anim-state "attackOneHand" :event "onAttackOneHandLock" :r-lock? true}
     {:anim-state "attackR" :event "onAttackREnd" :skill? true :end? true}
     {:anim-state "attackR" :event "onAttackRCall" :call? true :call-name :attack-r}
     {:anim-state "attackR" :event "onAttackRLockRelease" :r-release? true}
     {:anim-state "attackR" :event "onAttackRLock" :r-lock? true}
     {:anim-state "attackSlowDown" :event "onAttackSlowDownCall" :call? true :call-name :attack-slow-down}
     {:anim-state "attackSlowDown" :event "onAttackSlowDownEnd" :skill? true :end? true}]))

(def last-one-hand-combo (atom (js/Date.now)))

(def close-attack-distance-threshold 0.75)

(on :attack-one-hand
    (fn [player-id]
      (fire :ui-cooldown "attackOneHand")
      (let [enemy (st/get-other-player player-id)]
        (skills.effects/apply-effect-attack-one-hand enemy)
        (if false #_(> (rand-int 10) 8)
            (do
              (pc/set-anim-int (st/get-model-entity player-id) "health" 0)
              (st/disable-player-collision player-id)
              (st/set-health player-id 0))
            (st/set-health player-id (rand-int 100))))))

(on :attack-slow-down
    (fn [player-id]
      (fire :ui-cooldown "attackSlowDown")
      (let [enemy (st/get-other-player player-id)]
        (skills.effects/apply-effect-attack-slow-down enemy)
        (when false #_(> (rand-int 10) 8)
              (pc/set-anim-int (st/get-model-entity player-id) "health" 0)
              (st/disable-player-collision player-id)
              (st/set-health player-id 0)))))

(on :attack-r
    (fn [player-id]
      (fire :ui-cooldown "attackR")
      (let [enemy (st/get-other-player player-id)]
        (skills.effects/apply-effect-attack-r enemy)
        (if false #_(> (rand-int 10) 8)
            (do
              (pc/set-anim-int (st/get-model-entity player-id) "health" 0)
              (st/disable-player-collision player-id)
              (st/set-health player-id 0))
            (st/set-health player-id (rand-int 100))))))

(comment
  (sm/spawn 1)
  (sm/spawn-all)
  st/other-players
  )

(defn process-skills [e]
  ;; TODO add check if our char is alive
  (when-not (-> e .-event .-repeat)
    (let [model-entity (st/get-model-entity)
          active-state (pc/get-anim-state model-entity)
          selected-player-id (st/get-selected-player-id)]
      (m/process-cancellable-skills
        ["attackOneHand" "attackSlowDown" "attackR"]
        (j/get-in e [:event :code])
        active-state
        player)
      (cond
        (and (= active-state "attackOneHand")
             (skills/skill-pressed? e "attackR")
             (j/get player :can-r-attack-interrupt?)
             (st/enemy-selected? selected-player-id)
             (st/alive? selected-player-id)
             (<= (st/distance-to selected-player-id) close-attack-distance-threshold))
        (do
          (println "R combo!")
          (j/assoc-in! player [:skill->selected-player-id "attackR"] selected-player-id)
          (pc/set-anim-boolean model-entity "attackOneHand" false)
          (pc/set-anim-boolean model-entity "attackR" true))

        (and (= active-state "attackR")
             (skills/skill-pressed? e "attackOneHand")
             (j/get player :can-r-attack-interrupt?)
             (> (- (js/Date.now) @last-one-hand-combo) (utils/rand-between 750 1200))
             (st/cooldown-ready? "attackOneHand")
             (st/enemy-selected? selected-player-id)
             (st/alive? selected-player-id)
             (<= (st/distance-to selected-player-id) close-attack-distance-threshold))
        (do
          (println "one hand combo...!")
          (j/assoc-in! player [:skill->selected-player-id "attackOneHand"] selected-player-id)
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
        (do
          (j/assoc-in! player [:skill->selected-player-id "attackOneHand"] selected-player-id)
          (pc/set-anim-boolean (st/get-model-entity) "attackOneHand" true))

        (and
          (skills/idle-run-states active-state)
          (skills/skill-pressed? e "attackSlowDown")
          (st/cooldown-ready? "attackSlowDown")
          (st/enemy-selected? selected-player-id)
          (st/alive? selected-player-id)
          (<= (st/distance-to selected-player-id) close-attack-distance-threshold))
        (do
          (j/assoc-in! player [:skill->selected-player-id "attackSlowDown"] selected-player-id)
          (pc/set-anim-boolean (st/get-model-entity) "attackSlowDown" true))

        (and
          (skills/idle-run-states active-state)
          (skills/skill-pressed? e "attackR")
          (st/enemy-selected? selected-player-id)
          (st/alive? selected-player-id)
          (<= (st/distance-to selected-player-id) close-attack-distance-threshold))
        (do
          (j/assoc-in! player [:skill->selected-player-id "attackR"] selected-player-id)
          (pc/set-anim-boolean model-entity "attackR" true))

        (and
          (skills/skill-pressed? e "shieldWall")
          (st/cooldown-ready? "shieldWall"))
        (do
          (fire :ui-cooldown "shieldWall")
          (skills.effects/apply-effect-shield-wall player))

        (and
          (skills/skill-pressed? e "fleetFoot")
          (st/cooldown-ready? "fleetFoot")
          (not (j/get player :fleet-foot?)))
        (do
          (fire :ui-cooldown "fleetFoot")
          (skills.effects/apply-effect-fleet-foot player)
          (j/assoc! player
                    :fleet-foot? true
                    :speed (if (st/asas?) 850 700))
          (js/setTimeout
            #(do
               (j/assoc! player :fleet-foot? false)
               (j/assoc! player :speed 550)
               (pc/update-anim-speed (st/get-model-entity) "run" 1))
            (-> common/skills (get "fleetFoot") :cooldown (- 1500))))

        (and
          (skills/skill-pressed? e "hpPotion")
          (st/cooldown-ready? "hpPotion")
          (st/cooldown-ready? "mpPotion"))
        (do
          (fire :ui-cooldown "hpPotion")
          (skills.effects/apply-effect-hp-potion player))

        (and
          (skills/skill-pressed? e "mpPotion")
          (st/cooldown-ready? "hpPotion")
          (st/cooldown-ready? "mpPotion"))
        (do
          (fire :ui-cooldown "mpPotion")
          (skills.effects/apply-effect-mp-potion player))))))

(comment
  (st/set-health 100)
  (st/set-mana 90)
  (j/assoc! player :speed 850)
  (j/assoc! player :speed 700)
  (j/assoc! player :speed 550)
  player
  (pc/update-anim-speed (st/get-model-entity) "run" 2)
  (fire :re-init)

  (skills.effects/apply-effect-got-defense-break player)
  (skills.effects/apply-effect-hp-potion player)
  (skills.effects/apply-effect-mp-potion player)
  (skills.effects/apply-effect-got-cure player)
  (skills.effects/apply-effect-fleet-foot player)
  (j/get-in player [:effects :particle_defense_break_hands])
  )
