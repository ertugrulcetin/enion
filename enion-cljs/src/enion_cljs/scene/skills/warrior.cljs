(ns enion-cljs.scene.skills.warrior
  (:require
    [applied-science.js-interop :as j]
    [common.enion.skills :as common.skills]
    [enion-cljs.common :refer [fire on dlog]]
    [enion-cljs.scene.keyboard :as k]
    [enion-cljs.scene.network :as net :refer [dispatch-pro]]
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

(def last-one-hand-combo (volatile! (js/Date.now)))

(def attack-one-hand-cooldown (-> common.skills/skills (get "attackOneHand") :cooldown))

(def attack-one-hand-required-mana (-> common.skills/skills (get "attackOneHand") :required-mana))
(def attack-slow-down-required-mana (-> common.skills/skills (get "attackSlowDown") :required-mana))
(def attack-r-required-mana (-> common.skills/skills (get "attackR") :required-mana))
(def shield-required-mana (-> common.skills/skills (get "shieldWall") :required-mana))
(def fleet-food-required-mana (-> common.skills/skills (get "fleetFoot") :required-mana))

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

(defmethod skills/skill-response "attackOneHand" [params]
  (fire :ui-cooldown "attackOneHand")
  (let [selected-player-id (-> params :skill :selected-player-id)
        damage (-> params :skill :damage)
        enemy (st/get-other-player selected-player-id)]
    (skills.effects/apply-effect-attack-one-hand enemy)
    (fire :ui-send-msg {:to (j/get (st/get-other-player selected-player-id) :username)
                        :hit damage})))

(defmethod net/dispatch-pro-response :got-attack-one-hand-damage [params]
  (let [params (:got-attack-one-hand-damage params)
        damage (:damage params)
        player-id (:player-id params)]
    (skills.effects/apply-effect-attack-one-hand st/player)
    (fire :ui-send-msg {:from (j/get (st/get-other-player player-id) :username)
                        :damage damage})))

(defmethod skills/skill-response "attackSlowDown" [params]
  (fire :ui-cooldown "attackSlowDown")
  (let [selected-player-id (-> params :skill :selected-player-id)
        damage (-> params :skill :damage)
        enemy (st/get-other-player selected-player-id)]
    (skills.effects/apply-effect-attack-slow-down enemy)
    (fire :ui-send-msg {:to (j/get (st/get-other-player selected-player-id) :username)
                        :hit damage})))

(defmethod net/dispatch-pro-response :got-attack-slow-down-damage [params]
  (let [params (:got-attack-slow-down-damage params)
        damage (:damage params)
        player-id (:player-id params)]
    (skills.effects/apply-effect-attack-slow-down st/player)
    (fire :ui-send-msg {:from (j/get (st/get-other-player player-id) :username)
                        :damage damage})))

(defmethod skills/skill-response "attackR" [params]
  (fire :ui-cooldown "attackR")
  (let [selected-player-id (-> params :skill :selected-player-id)
        damage (-> params :skill :damage)
        enemy (st/get-other-player selected-player-id)]
    (skills.effects/apply-effect-attack-r enemy)
    (fire :ui-send-msg {:to (j/get (st/get-other-player selected-player-id) :username)
                        :hit damage})))

(defmethod net/dispatch-pro-response :got-attack-r-damage [params]
  (let [params (:got-attack-r-damage params)
        damage (:damage params)
        player-id (:player-id params)]
    (skills.effects/apply-effect-attack-r st/player)
    (fire :ui-send-msg {:from (j/get (st/get-other-player player-id) :username)
                        :damage damage})))

(comment
  (sm/spawn 1)
  (sm/spawn-all)
  st/other-players
  )

(let [too-far-msg {:too-far true}]
  (defn- close-for-attack? [selected-player-id]
    (let [result (<= (st/distance-to selected-player-id) common.skills/close-attack-distance-threshold)]
      (when-not result
        (fire :ui-send-msg too-far-msg))
      result)))

(defn- r-combo? [e active-state selected-player-id]
  (and (= active-state "attackOneHand")
       (skills/skill-pressed? e "attackR")
       (j/get player :can-r-attack-interrupt?)
       (st/enemy-selected? selected-player-id)
       (st/alive? selected-player-id)
       (close-for-attack? selected-player-id)))

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
       (close-for-attack? selected-player-id)))

(defn- idle? [active-state]
  (and (= "idle" active-state) (k/pressing-wasd?)))

(defn- jump? [e active-state]
  (and (skills/idle-run-states active-state)
       (pc/key? e :KEY_SPACE) (j/get player :on-ground?)))

(defn- attack-one-hand? [e active-state selected-player-id]
  (and
    (skills/idle-run-states active-state)
    (skills/skill-pressed? e "attackOneHand")
    (st/cooldown-ready? "attackOneHand")
    (st/enemy-selected? selected-player-id)
    (st/alive? selected-player-id)
    (st/enough-mana? attack-one-hand-required-mana)
    (close-for-attack? selected-player-id)))

(defn- attack-slow-down? [e active-state selected-player-id]
  (and
    (skills/idle-run-states active-state)
    (skills/skill-pressed? e "attackSlowDown")
    (st/cooldown-ready? "attackSlowDown")
    (st/enemy-selected? selected-player-id)
    (st/alive? selected-player-id)
    (st/enough-mana? attack-slow-down-required-mana)
    (close-for-attack? selected-player-id)))

(defn- attack-r? [e active-state selected-player-id]
  (and
    (skills/idle-run-states active-state)
    (skills/skill-pressed? e "attackR")
    (st/enemy-selected? selected-player-id)
    (st/alive? selected-player-id)
    (st/enough-mana? attack-r-required-mana)
    (close-for-attack? selected-player-id)))

(defn- shield-wall? [e]
  (and
    (skills/skill-pressed? e "shieldWall")
    (st/cooldown-ready? "shieldWall")
    (st/enough-mana? shield-required-mana)))

(defn- fleet-foot? [e]
  (and
    (skills/skill-pressed? e "fleetFoot")
    (st/cooldown-ready? "fleetFoot")
    (st/enough-mana? fleet-food-required-mana)
    (not (j/get player :fleet-foot?))))

(defn- hp-potion? [e]
  (and
    (skills/skill-pressed? e "hpPotion")
    (st/cooldown-ready? "hpPotion")
    (st/cooldown-ready? "mpPotion")))

(defn- mp-potion? [e]
  (and
    (skills/skill-pressed? e "mpPotion")
    (st/cooldown-ready? "hpPotion")
    (st/cooldown-ready? "mpPotion")))

(defn process-skills [e]
  ;; TODO add check if our char is alive -- APPLY FOR ALL CLASSES - then DELETE THIS TODO
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
        (r-combo? e active-state selected-player-id)
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
          (vreset! last-one-hand-combo (js/Date.now)))

        (idle? active-state)
        (pc/set-anim-boolean model-entity "run" true)

        (jump? e active-state)
        (pc/set-anim-boolean model-entity "jump" true)

        (attack-one-hand? e active-state selected-player-id)
        (do
          (j/assoc-in! player [:skill->selected-player-id "attackOneHand"] selected-player-id)
          (pc/set-anim-boolean (st/get-model-entity) "attackOneHand" true))

        (attack-slow-down? e active-state selected-player-id)
        (do
          (j/assoc-in! player [:skill->selected-player-id "attackSlowDown"] selected-player-id)
          (pc/set-anim-boolean (st/get-model-entity) "attackSlowDown" true))

        (attack-r? e active-state selected-player-id)
        (do
          (j/assoc-in! player [:skill->selected-player-id "attackR"] selected-player-id)
          (pc/set-anim-boolean model-entity "attackR" true))

        (shield-wall? e)
        (do
          (fire :ui-cooldown "shieldWall")
          (skills.effects/apply-effect-shield-wall player))

        (fleet-foot? e)
        (do
          (dispatch-pro :skill {:skill "fleetFoot"})
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
            (-> common.skills/skills (get "fleetFoot") :cooldown (- 1500))))

        (hp-potion? e)
        (do
          (fire :ui-cooldown "hpPotion")
          (skills.effects/apply-effect-hp-potion player))

        (mp-potion? e)
        (do
          (fire :ui-cooldown "mpPotion")
          (skills.effects/apply-effect-mp-potion player))))))

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
