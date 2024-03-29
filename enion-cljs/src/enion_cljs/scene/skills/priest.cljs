(ns enion-cljs.scene.skills.priest
  (:require
    [applied-science.js-interop :as j]
    [common.enion.skills :as common.skills]
    [enion-cljs.common :refer [fire on dlog]]
    [enion-cljs.scene.network :as net :refer [dispatch-pro]]
    [enion-cljs.scene.pc :as pc]
    [enion-cljs.scene.skills.core :as skills]
    [enion-cljs.scene.skills.effects :as skills.effects]
    [enion-cljs.scene.states :refer [player get-model-entity]]
    [enion-cljs.scene.states :as st]
    [enion-cljs.scene.utils :as utils])
  (:require-macros
    [enion-cljs.scene.macros :as m]))

(def events
  (concat skills/common-states
          [{:anim-state "breakDefense" :event "onBreakDefenseEnd" :skill? true :end? true}
           {:anim-state "breakDefense" :event "onBreakDefenseCall" :call? true}
           {:anim-state "heal" :event "onHealEnd" :skill? true :end? true}
           {:anim-state "heal" :event "onHealCall" :call? true}
           {:anim-state "attackR" :event "onAttackREnd" :skill? true :end? true}
           {:anim-state "attackR" :event "onAttackRCall" :call? true}
           {:anim-state "attackPriest" :event "onAttackPriestEnd" :skill? true :end? true}
           {:anim-state "attackPriest" :event "onAttackPriestCall" :call? true}
           {:anim-state "cure" :event "onCureCall" :call? true}
           {:anim-state "cure" :event "onCureEnd" :skill? true :end? true}]))

(def heal-required-mana (skills/get-required-mana "heal"))
(def cure-required-mana (skills/get-required-mana "cure"))
(def break-defense-required-mana (skills/get-required-mana "breakDefense"))
(def attack-priest-required-mana (skills/get-required-mana "attackPriest"))

(def too-far-msg {:too-far true})

(defn close-for-skill? [selected-player-id]
  (let [close? (or (nil? selected-player-id)
                   (st/enemy-selected? selected-player-id)
                   (st/npc-selected?)
                   (and selected-player-id
                        (st/ally-selected? selected-player-id)
                        (<= (st/distance-to selected-player-id) common.skills/priest-skills-distance-threshold)))]
    (when (and (st/ally-selected? selected-player-id) (not close?))
      (fire :show-text too-far-msg))
    close?))

(defn enemy-close-for-skill? [selected-player-id]
  (let [result (<= (st/distance-to selected-player-id) common.skills/priest-skills-distance-threshold)]
    (when-not result
      (fire :show-text too-far-msg))
    result))

(defn heal? [e selected-player-id npc?]
  (and (skills/skill-pressed? e "heal")
       (st/cooldown-ready? "heal")
       (or (and selected-player-id (st/alive? selected-player-id npc?))
           (and (not selected-player-id) (st/alive?)))
       (st/enough-mana? heal-required-mana)
       (close-for-skill? selected-player-id)))

(defn cure? [e selected-player-id npc?]
  (and (skills/skill-pressed? e "cure")
       (st/cooldown-ready? "cure")
       (or (and selected-player-id (st/alive? selected-player-id npc?))
           (and (not selected-player-id) (st/alive?)))
       (st/enough-mana? cure-required-mana)
       (close-for-skill? selected-player-id)))

(defn break-defense? [e selected-player-id]
  (and (skills/skill-pressed? e "breakDefense")
       (st/cooldown-ready? "breakDefense")
       (skills/can-attack-to-enemy? selected-player-id)
       (st/enough-mana? break-defense-required-mana)
       (enemy-close-for-skill? selected-player-id)))

(let [heal-msg {:hp (-> common.skills/skills (get "heal") :hp)}]
  (defmethod skills/skill-response "heal" [params]
    (fire :ui-cooldown "heal")
    (st/play-sound "heal")
    (let [selected-player-id (-> params :skill :selected-player-id)]
      (when (= selected-player-id net/current-player-id)
        (skills.effects/add-to-healed-ids)
        (fire :show-text heal-msg)))
    (fire :show-text {:mp-used heal-required-mana})))

(let [cure-msg {:cure true}]
  (defmethod skills/skill-response "cure" [params]
    (fire :ui-cooldown "cure")
    (st/play-sound "cure")
    (let [selected-player-id (-> params :skill :selected-player-id)]
      (when (= selected-player-id net/current-player-id)
        (skills.effects/apply-effect-got-cure player)
        (fire :show-text cure-msg)
        (fire :ui-cured))
      (fire :show-text {:mp-used cure-required-mana}))))

(defmethod skills/skill-response "breakDefense" [params]
  (fire :show-text {:break-defense true})
  (fire :show-text {:mp-used break-defense-required-mana})
  (fire :ui-cooldown "breakDefense")
  (st/play-sound "breakDefense"))

(defmethod skills/skill-response "attackPriest" [params]
  (fire :ui-cooldown "attackPriest")
  (st/play-sound "attackPriest")
  (let [selected-player-id (-> params :skill :selected-player-id)
        damage (-> params :skill :damage)
        npc? (-> params :skill :npc?)
        enemy (if npc?
                (st/get-npc selected-player-id)
                (st/get-other-player selected-player-id))]
    (skills.effects/apply-effect-attack-priest enemy)
    (fire :show-text {:hit damage}))
  (fire :show-text {:mp-used attack-priest-required-mana}))

(defn- get-selected-player-id-for-priest-skill [selected-player-id npc?]
  (if (st/enemy-selected? selected-player-id npc?)
    net/current-player-id
    (or selected-player-id net/current-player-id)))

(defn- attack-priest? [e active-state selected-player-id]
  (and
    (skills/idle-run-states active-state)
    (skills/skill-pressed? e "attackPriest")
    (st/cooldown-ready? "attackPriest")
    (skills/can-attack-to-enemy? selected-player-id)
    (st/enough-mana? attack-priest-required-mana)
    (skills/close-for-attack? selected-player-id)))

(defn process-skills [e]
  (when (and (not (-> e .-event .-repeat)) (st/alive?))
    (let [model-entity (get-model-entity)
          active-state (pc/get-anim-state model-entity)
          selected-player-id (st/get-selected-player-id)
          npc? (st/npc-selected?)]
      (m/process-cancellable-skills
        ["attackR" "breakDefense" "heal" "cure" "attackPriest"]
        (j/get-in e [:event :code])
        active-state
        player)
      (cond
        (heal? e selected-player-id npc?)
        (let [selected-player-id (get-selected-player-id-for-priest-skill selected-player-id npc?)]
          (j/assoc-in! player [:skill->selected-player-id "heal"] selected-player-id)
          (pc/set-anim-boolean model-entity "heal" true)
          (skills.effects/apply-effect-heal-particles player)
          (st/look-at-selected-player)
          (st/cancel-target-pos))

        (cure? e selected-player-id npc?)
        (let [selected-player-id (get-selected-player-id-for-priest-skill selected-player-id npc?)]
          (j/assoc-in! player [:skill->selected-player-id "cure"] selected-player-id)
          (pc/set-anim-boolean model-entity "cure" true)
          (skills.effects/apply-effect-cure-particles player)
          (st/look-at-selected-player)
          (st/cancel-target-pos))

        (break-defense? e selected-player-id)
        (do
          (j/assoc-in! player [:skill->selected-player-id "breakDefense"] selected-player-id)
          (j/assoc-in! player [:skill->selected-enemy-npc? "breakDefense"] npc?)
          (pc/set-anim-boolean model-entity "breakDefense" true)
          (skills.effects/apply-effect-defense-break-particles player)
          (st/look-at-selected-player)
          (st/cancel-target-pos))

        (attack-priest? e active-state selected-player-id)
        (do
          (j/assoc-in! player [:skill->selected-player-id "attackPriest"] selected-player-id)
          (j/assoc-in! player [:skill->selected-enemy-npc? "attackPriest"] npc?)
          (pc/set-anim-boolean (st/get-model-entity) "attackPriest" true)
          (st/look-at-selected-player))

        (skills/run? active-state)
        (pc/set-anim-boolean model-entity "run" true)

        (skills/jump? e active-state)
        (pc/set-anim-boolean model-entity "jump" true)

        (skills/attack-r? e active-state selected-player-id)
        (do
          (j/assoc-in! player [:skill->selected-player-id "attackR"] selected-player-id)
          (j/assoc-in! player [:skill->selected-enemy-npc? "attackR"] npc?)
          (pc/set-anim-boolean model-entity "attackR" true)
          (st/look-at-selected-player))

        (skills/fleet-foot? e)
        (dispatch-pro :skill {:skill "fleetFoot"})

        (skills/hp-potion? e)
        (dispatch-pro :skill {:skill "hpPotion"})

        (skills/mp-potion? e)
        (dispatch-pro :skill {:skill "mpPotion"})))))
