(ns enion-cljs.scene.skills.mage
  (:require
    [applied-science.js-interop :as j]
    [common.enion.skills :as common.skills]
    [enion-cljs.common :as common :refer [dlog fire]]
    [enion-cljs.scene.entities.camera :as entity.camera]
    [enion-cljs.scene.keyboard :as k]
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
  (concat
    skills/common-states
    [{:anim-state "attackRange" :event "onAttackRangeEnd" :skill? true :end? true}
     {:anim-state "attackRange" :event "onAttackRangeCall" :call? true}
     {:anim-state "attackSingle" :event "onAttackSingleEnd" :skill? true :end? true}
     {:anim-state "attackSingle" :event "onAttackSingleCall" :call? true}
     {:anim-state "attackIce" :event "onAttackIceEnd" :skill? true :end? true}
     {:anim-state "attackIce" :event "onAttackIceCall" :call? true}
     {:anim-state "attackR" :event "onAttackREnd" :skill? true :end? true}
     {:anim-state "attackR" :event "onAttackRCall" :call? true}
     {:anim-state "teleport" :event "onTeleportCall" :call? true}
     {:anim-state "teleport" :event "onTeleportEnd" :skill? true :end? true}]))

(def attack-range-required-mana (-> common.skills/skills (get "attackRange") :required-mana))
(def attack-single-required-mana (-> common.skills/skills (get "attackSingle") :required-mana))
(def attack-ice-required-mana (-> common.skills/skills (get "attackIce") :required-mana))
(def teleport-required-mana (-> common.skills/skills (get "teleport") :required-mana))

(let [too-far-msg {:too-far true}]
  (defn throw-nova [e]
    (when (j/get player :positioning-nova?)
      (let [result (st/get-closest-terrain-hit e)
            hit-entity-name (j/get-in result [:entity :name])
            nova-pos (j/get result :point)]
        (if (= "terrain" hit-entity-name)
          (do
            (if (> (pc/distance nova-pos (pc/get-pos (st/get-player-entity)))
                   common.skills/attack-range-distance-threshold)
              (fire :ui-send-msg too-far-msg)
              (let [model-entity (st/get-model-entity)
                    char-pos (pc/get-pos model-entity)]
                (pc/set-anim-boolean (st/get-model-entity) "attackRange" true)
                (skills.effects/apply-effect-flame-particles player)
                (j/assoc! player :nova-pos nova-pos)
                (pc/look-at model-entity (j/get nova-pos :x) (j/get char-pos :y) (j/get nova-pos :z) true)))
            (j/assoc! player :positioning-nova? false)
            (pc/set-nova-circle-pos))
          (do
            (j/assoc! player :positioning-nova? false)
            (pc/set-nova-circle-pos)))))))

(defn create-throw-nova-fn [entity]
  (let [temp-first-pos #js {}
        temp-final-pos #js {}
        temp-final-scale #js {:x 5 :y 5 :z 5}
        opacity #js {:opacity 1}
        last-opacity #js {:opacity 0}]
    (fn [pos]
      (j/assoc! entity :enabled true)
      (j/assoc! temp-first-pos :x (j/get pos :x) :y (+ (j/get pos :y) 5) :z (j/get pos :z))
      (j/assoc! temp-final-pos :x (j/get pos :x) :y (j/get pos :y) :z (j/get pos :z))
      (let [tween-pos (-> (j/call entity :tween temp-first-pos)
                          (j/call :to temp-final-pos 0.5 pc/expo-in))
            _ (j/call tween-pos :on "update"
                      (fn []
                        (pc/set-pos entity (j/get temp-first-pos :x) (j/get temp-first-pos :y) (j/get temp-first-pos :z))))
            _ (pc/set-loc-scale entity 0.2)
            first-scale (pc/get-loc-scale entity)
            tween-scale (-> (j/call entity :tween first-scale)
                            (j/call :to temp-final-scale 1 pc/linear))
            _ (pc/set-mesh-opacity entity 1)
            _ (j/assoc! opacity :opacity 1)
            tween-opacity (-> (j/call entity :tween opacity)
                              (j/call :to last-opacity 2.5 pc/linear))
            _ (j/call tween-opacity :on "update"
                      (fn []
                        (pc/set-mesh-opacity entity (j/get opacity :opacity))
                        ;; nova entity is a child entity of the mage entity so we're kinda updating its local pos
                        ;; so had to set in here as well, this tween takes the longest time
                        (pc/set-pos entity (j/get temp-first-pos :x) (j/get temp-first-pos :y) (j/get temp-first-pos :z))))
            _ (j/call tween-opacity :on "complete"
                      (fn []
                        (j/call-in entity [:children 0 :particlesystem :stop])
                        (j/call-in entity [:children 0 :particlesystem :reset])
                        (j/assoc! entity :enabled false)))]
        (j/call tween-pos :start)
        (j/call tween-scale :start)
        (j/call tween-opacity :start)
        (j/call-in entity [:children 0 :particlesystem :play])
        nil))))

(defn attack-range? [e active-state]
  (and (skills/idle-run-states active-state)
       (skills/skill-pressed? e "attackRange")
       (st/cooldown-ready? "attackRange")
       (st/enough-mana? attack-range-required-mana)))

(let [temp-pos (pc/vec3)]
  (defmethod skills/skill-response "attackRange" [params]
    (fire :ui-cooldown "attackRange")
    (let [damaged-enemies (-> params :skill :damages)
          x (-> params :skill :x)
          y (-> params :skill :y)
          z (-> params :skill :z)]
      (pc/setv temp-pos x y z)
      ((j/get-in player [:skills :throw-nova]) temp-pos)
      (doseq [enemy damaged-enemies]
        (fire :ui-send-msg {:to (j/get (if (:npc? enemy)
                                         (st/get-npc (:id enemy))
                                         (st/get-other-player (:id enemy))) :username)
                            :hit (:damage enemy)}))
      (st/play-sound "attackRange"))))

(defmethod skills/skill-response "attackSingle" [params]
  (fire :ui-cooldown "attackSingle")
  (let [selected-player-id (-> params :skill :selected-player-id)
        damage (-> params :skill :damage)
        npc? (-> params :skill :npc?)
        enemy (if npc?
                (st/get-npc selected-player-id)
                (st/get-other-player selected-player-id))]
    (fire :ui-send-msg {:to (j/get enemy :username)
                        :hit damage})
    (when (not (utils/tutorial-finished? :how-to-cast-skills?))
      (utils/finish-tutorial-step :how-to-cast-skills?))))

(defmethod skills/skill-response "attackIce" [params]
  (fire :ui-cooldown "attackIce")
  (let [selected-player-id (-> params :skill :selected-player-id)
        damage (-> params :skill :damage)
        npc? (-> params :skill :npc?)
        enemy (if npc?
                (st/get-npc selected-player-id)
                (st/get-other-player selected-player-id))]
    (fire :ui-send-msg {:to (j/get enemy :username)
                        :hit damage})))

(defmethod skills/skill-response "teleport" [_]
  (fire :ui-cooldown "teleport"))

(let [too-far-msg {:too-far true}]
  (defn close-for-attack-single? [selected-player-id]
    (let [result (<= (st/distance-to selected-player-id) common.skills/attack-single-distance-threshold)]
      (when-not result
        (fire :ui-send-msg too-far-msg))
      result)))

(defn- attack-single? [e active-state selected-player-id]
  (and (skills/idle-run-states active-state)
       (skills/skill-pressed? e "attackSingle")
       (skills/can-attack-to-enemy? selected-player-id)
       (st/cooldown-ready? "attackSingle")
       (st/enough-mana? attack-single-required-mana)
       (close-for-attack-single? selected-player-id)))

(defn- attack-ice? [e active-state selected-player-id]
  (and (skills/idle-run-states active-state)
       (skills/skill-pressed? e "attackIce")
       (st/cooldown-ready? "attackIce")
       (skills/can-attack-to-enemy? selected-player-id)
       (st/enough-mana? attack-single-required-mana)
       (close-for-attack-single? selected-player-id)))

(defn teleport? [e active-state selected-player-id]
  (and (skills/idle-run-states active-state)
       (skills/skill-pressed? e "teleport")
       (st/alive? selected-player-id)
       (st/ally-selected? selected-player-id)
       (st/cooldown-ready? "teleport")
       (st/enough-mana? teleport-required-mana)
       (st/party-member? selected-player-id)))

(defn process-skills [e]
  (when (and (not (-> e .-event .-repeat)) (st/alive?))
    (let [model-entity (get-model-entity)
          active-state (pc/get-anim-state model-entity)
          selected-player-id (st/get-selected-player-id)
          npc? (st/npc-selected?)]
      (m/process-cancellable-skills
        ["attackRange" "attackSingle" "attackIce" "attackR" "teleport"]
        (j/get-in e [:event :code])
        active-state
        player)
      (cond
        (attack-range? e active-state)
        (do
          (j/assoc! player :positioning-nova? true)
          (st/show-nova-circle e))

        (attack-single? e active-state selected-player-id)
        (do
          (j/assoc! player :positioning-nova? false)
          (pc/set-nova-circle-pos)
          (j/assoc-in! player [:skill->selected-player-id "attackSingle"] selected-player-id)
          (j/assoc-in! player [:skill->selected-enemy-npc? "attackSingle"] npc?)
          (pc/set-anim-boolean model-entity "attackSingle" true)
          (skills.effects/apply-effect-fire-hands player)
          (st/look-at-selected-player)
          (st/play-sound "attackSingle"))

        (attack-ice? e active-state selected-player-id)
        (do
          (j/assoc! player :positioning-nova? false)
          (pc/set-nova-circle-pos)
          (j/assoc-in! player [:skill->selected-player-id "attackIce"] selected-player-id)
          (j/assoc-in! player [:skill->selected-enemy-npc? "attackIce"] npc?)
          (pc/set-anim-boolean model-entity "attackIce" true)
          (skills.effects/apply-effect-ice-hands player)
          (st/look-at-selected-player)
          (st/play-sound "attackIce"))

        (teleport? e active-state selected-player-id)
        (do
          (j/assoc! player :positioning-nova? false)
          (pc/set-nova-circle-pos)
          (j/assoc-in! player [:skill->selected-player-id "teleport"] selected-player-id)
          (pc/set-anim-boolean model-entity "teleport" true)
          (st/play-sound "teleport"))

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
