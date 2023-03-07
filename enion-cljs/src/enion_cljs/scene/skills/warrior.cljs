(ns enion-cljs.scene.skills.warrior
  (:require
    [applied-science.js-interop :as j]
    [common.enion.skills :as common.skills]
    [enion-cljs.common :refer [fire on dlog]]
    [enion-cljs.scene.network :refer [dispatch-pro]]
    [enion-cljs.scene.pc :as pc]
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
     {:anim-state "attackOneHand" :event "onAttackOneHandCall" :call? true}
     {:anim-state "attackOneHand" :event "onAttackOneHandLockRelease" :r-release? true}
     {:anim-state "attackOneHand" :event "onAttackOneHandLock" :r-lock? true}
     {:anim-state "attackR" :event "onAttackREnd" :skill? true :end? true}
     {:anim-state "attackR" :event "onAttackRCall" :call? true}
     {:anim-state "attackR" :event "onAttackRLockRelease" :r-release? true}
     {:anim-state "attackR" :event "onAttackRLock" :r-lock? true}
     {:anim-state "attackSlowDown" :event "onAttackSlowDownCall" :call? true}
     {:anim-state "attackSlowDown" :event "onAttackSlowDownEnd" :skill? true :end? true}]))

(def last-one-hand-combo (volatile! (js/Date.now)))

(def attack-one-hand-cooldown (-> common.skills/skills (get "attackOneHand") :cooldown))
(def attack-one-hand-required-mana (-> common.skills/skills (get "attackOneHand") :required-mana))
(def attack-slow-down-required-mana (-> common.skills/skills (get "attackSlowDown") :required-mana))
(def shield-required-mana (-> common.skills/skills (get "shieldWall") :required-mana))
(def battle-fury-required-mana (-> common.skills/skills (get "battleFury") :required-mana))

(defmethod skills/skill-response "attackOneHand" [params]
  (fire :ui-cooldown "attackOneHand")
  (let [selected-player-id (-> params :skill :selected-player-id)
        damage (-> params :skill :damage)]
    (fire :ui-send-msg {:to (j/get (st/get-other-player selected-player-id) :username)
                        :hit damage})
    (when (not (utils/tutorial-finished? :how-to-cast-skills?))
      (utils/finish-tutorial-step :how-to-cast-skills?))))

(defmethod skills/skill-response "attackSlowDown" [params]
  (fire :ui-cooldown "attackSlowDown")
  (let [selected-player-id (-> params :skill :selected-player-id)
        damage (-> params :skill :damage)]
    (fire :ui-send-msg {:to (j/get (st/get-other-player selected-player-id) :username)
                        :hit damage})))

(defmethod skills/skill-response "shieldWall" [_]
  (fire :ui-cooldown "shieldWall")
  (skills.effects/apply-effect-shield-wall player)
  (st/play-sound "pv-sw"))

(defmethod skills/skill-response "battleFury" [_]
  (fire :ui-cooldown "battleFury")
  (skills.effects/apply-effect-battle-fury player)
  (st/play-sound "attackBoost"))

(defn- one-hand-combo? [e active-state selected-player-id]
  (and (= active-state "attackR")
       (skills/skill-pressed? e "attackOneHand")
       (j/get player :can-r-attack-interrupt?)
       (> (- (js/Date.now) @last-one-hand-combo) (utils/rand-between
                                                   attack-one-hand-cooldown
                                                   (+ attack-one-hand-cooldown 400)))
       (st/cooldown-ready? "attackOneHand")
       (st/enemy-selected? selected-player-id)
       (st/alive? selected-player-id)
       (st/enough-mana? attack-one-hand-required-mana)
       (skills/close-for-attack? selected-player-id)))

(defn- attack-one-hand? [e active-state selected-player-id]
  (and
    (skills/idle-run-states active-state)
    (skills/skill-pressed? e "attackOneHand")
    (st/cooldown-ready? "attackOneHand")
    (st/enemy-selected? selected-player-id)
    (st/alive? selected-player-id)
    (st/enough-mana? attack-one-hand-required-mana)
    (skills/close-for-attack? selected-player-id)))

(defn- attack-slow-down? [e active-state selected-player-id]
  (and
    (skills/idle-run-states active-state)
    (skills/skill-pressed? e "attackSlowDown")
    (st/cooldown-ready? "attackSlowDown")
    (st/enemy-selected? selected-player-id)
    (st/alive? selected-player-id)
    (st/enough-mana? attack-slow-down-required-mana)
    (skills/close-for-attack? selected-player-id)))

(defn- shield-wall? [e]
  (and
    (skills/skill-pressed? e "shieldWall")
    (st/cooldown-ready? "shieldWall")
    (st/enough-mana? shield-required-mana)))

(defn- battle-fury? [e]
  (and
    (skills/skill-pressed? e "battleFury")
    (st/cooldown-ready? "battleFury")
    (st/enough-mana? battle-fury-required-mana)))

(defn process-skills [e]
  (when (and (not (-> e .-event .-repeat)) (st/alive?))
    (let [model-entity (st/get-model-entity)
          active-state (pc/get-anim-state model-entity)
          selected-player-id (st/get-selected-player-id)]
      (m/process-cancellable-skills
        ["attackOneHand" "attackSlowDown" "attackR"]
        (j/get-in e [:event :code])
        active-state
        player)
      (cond
        (skills/r-combo? e "attackOneHand" active-state selected-player-id)
        (do
          (dlog "R combo!")
          (j/assoc-in! player [:skill->selected-player-id "attackR"] selected-player-id)
          (pc/set-anim-boolean model-entity "attackOneHand" false)
          (pc/set-anim-boolean model-entity "attackR" true))

        (one-hand-combo? e active-state selected-player-id)
        (do
          (dlog "one hand combo...!")
          (j/assoc-in! player [:skill->selected-player-id "attackOneHand"] selected-player-id)
          (pc/set-anim-boolean model-entity "attackR" false)
          (pc/set-anim-boolean model-entity "attackOneHand" true)
          (vreset! last-one-hand-combo (js/Date.now))
          (st/play-sound "attackOneHand"))

        (skills/run? active-state)
        (pc/set-anim-boolean model-entity "run" true)

        (skills/jump? e active-state)
        (pc/set-anim-boolean model-entity "jump" true)

        (attack-one-hand? e active-state selected-player-id)
        (do
          (j/assoc-in! player [:skill->selected-player-id "attackOneHand"] selected-player-id)
          (pc/set-anim-boolean (st/get-model-entity) "attackOneHand" true)
          (st/play-sound "attackOneHand"))

        (attack-slow-down? e active-state selected-player-id)
        (do
          (j/assoc-in! player [:skill->selected-player-id "attackSlowDown"] selected-player-id)
          (pc/set-anim-boolean (st/get-model-entity) "attackSlowDown" true)
          (st/play-sound "attackSlowDown"))

        (skills/attack-r? e active-state selected-player-id)
        (do
          (j/assoc-in! player [:skill->selected-player-id "attackR"] selected-player-id)
          (pc/set-anim-boolean model-entity "attackR" true)
          (st/look-at-selected-player))

        (shield-wall? e)
        (dispatch-pro :skill {:skill "shieldWall"})

        (battle-fury? e)
        (dispatch-pro :skill {:skill "battleFury"})

        (skills/fleet-foot? e)
        (dispatch-pro :skill {:skill "fleetFoot"})

        (skills/hp-potion? e)
        (dispatch-pro :skill {:skill "hpPotion"})

        (skills/mp-potion? e)
        (dispatch-pro :skill {:skill "mpPotion"})))))

(comment
  (dispatch-pro :skill {:skill "hpPotion"})
  (dispatch-pro :skill {:skill "mpPotion"})
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
