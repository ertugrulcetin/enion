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
    [enion-cljs.scene.states :as st])
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
           {:anim-state "cure" :event "onCureCall" :call? true}
           {:anim-state "cure" :event "onCureEnd" :skill? true :end? true}]))

(def heal-required-mana (-> common.skills/skills (get "heal") :required-mana))
(def cure-required-mana (-> common.skills/skills (get "cure") :required-mana))
(def break-defense-required-mana (-> common.skills/skills (get "breakDefense") :required-mana))

(let [too-far-msg {:too-far true}]
  (defn close-for-skill?
    ([selected-player-id]
     (close-for-skill? selected-player-id false))
    ([selected-player-id enemy?]
     (let [result (or (and (not enemy?) (nil? selected-player-id))
                      (<= (st/distance-to selected-player-id) common.skills/priest-skills-distance-threshold))]
       (when-not result
         (fire :ui-send-msg too-far-msg))
       result))))

(defn heal? [e selected-player-id]
  (and (skills/skill-pressed? e "heal")
       (st/cooldown-ready? "heal")
       (or (and selected-player-id (st/alive? selected-player-id))
           (and (not selected-player-id) (st/alive?)))
       (st/enough-mana? heal-required-mana)
       (close-for-skill? selected-player-id)))

(defn cure? [e selected-player-id]
  (and (skills/skill-pressed? e "cure")
       (st/cooldown-ready? "cure")
       (or (and selected-player-id (st/alive? selected-player-id))
           (and (not selected-player-id) (st/alive?)))
       (st/enough-mana? cure-required-mana)
       (close-for-skill? selected-player-id)))

(defn break-defense? [e selected-player-id]
  (and (skills/skill-pressed? e "breakDefense")
       (st/cooldown-ready? "breakDefense")
       (st/enemy-selected? selected-player-id)
       (st/alive? selected-player-id)
       (st/enough-mana? break-defense-required-mana)
       (close-for-skill? selected-player-id true)))

(let [heal-msg {:heal true}]
  (defmethod skills/skill-response "heal" [params]
    (fire :ui-cooldown "heal")
    (let [selected-player-id (-> params :skill :selected-player-id)]
      (when (= selected-player-id net/current-player-id)
        (skills.effects/add-to-healed-ids)
        (fire :ui-send-msg heal-msg)))))

(let [cure-msg {:cure true}]
  (defmethod skills/skill-response "cure" [params]
    (fire :ui-cooldown "cure")
    (let [selected-player-id (-> params :skill :selected-player-id)]
      (when (= selected-player-id net/current-player-id)
        (skills.effects/apply-effect-got-cure player)
        (fire :ui-send-msg cure-msg)))))

(defmethod skills/skill-response "breakDefense" [_]
  (fire :ui-cooldown "breakDefense"))

(defn- get-selected-player-id-for-priest-skill [selected-player-id]
  (if (st/enemy-selected? selected-player-id)
    net/current-player-id
    (or selected-player-id net/current-player-id)))

(defn process-skills [e]
  (when (and (not (-> e .-event .-repeat)) (st/alive?))
    (let [model-entity (get-model-entity)
          active-state (pc/get-anim-state model-entity)
          selected-player-id (st/get-selected-player-id)]
      (m/process-cancellable-skills
        ["attackR" "breakDefense" "heal" "cure"]
        (j/get-in e [:event :code])
        active-state
        player)
      (cond
        (heal? e selected-player-id)
        (let [selected-player-id (get-selected-player-id-for-priest-skill selected-player-id)]
          (j/assoc-in! player [:skill->selected-player-id "heal"] selected-player-id)
          (pc/set-anim-boolean model-entity "heal" true)
          (skills.effects/apply-effect-heal-particles player)
          (st/look-at-selected-player)
          (st/play-sound "heal"))

        (cure? e selected-player-id)
        (let [selected-player-id (get-selected-player-id-for-priest-skill selected-player-id)]
          (j/assoc-in! player [:skill->selected-player-id "cure"] selected-player-id)
          (pc/set-anim-boolean model-entity "cure" true)
          (skills.effects/apply-effect-cure-particles player)
          (st/look-at-selected-player)
          (st/play-sound "cure"))

        (break-defense? e selected-player-id)
        (do
          (j/assoc-in! player [:skill->selected-player-id "breakDefense"] selected-player-id)
          (pc/set-anim-boolean model-entity "breakDefense" true)
          (skills.effects/apply-effect-defense-break-particles player)
          (st/look-at-selected-player)
          (st/play-sound "breakDefense"))

        (skills/run? active-state)
        (pc/set-anim-boolean model-entity "run" true)

        (skills/jump? e active-state)
        (pc/set-anim-boolean model-entity "jump" true)

        (skills/attack-r? e active-state selected-player-id)
        (do
          (j/assoc-in! player [:skill->selected-player-id "attackR"] selected-player-id)
          (pc/set-anim-boolean model-entity "attackR" true)
          (st/look-at-selected-player))

        (skills/fleet-foot? e)
        (dispatch-pro :skill {:skill "fleetFoot"})

        (skills/hp-potion? e)
        (dispatch-pro :skill {:skill "hpPotion"})

        (skills/mp-potion? e)
        (dispatch-pro :skill {:skill "mpPotion"})))))
