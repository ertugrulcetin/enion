(ns enion-cljs.scene.skills.core
  (:require
    [applied-science.js-interop :as j]
    [common.enion.skills :as common.skills]
    [enion-cljs.common :as common :refer [fire]]
    [enion-cljs.scene.keyboard :as k]
    [enion-cljs.scene.network :as net :refer [dispatch-pro]]
    [enion-cljs.scene.pc :as pc]
    [enion-cljs.scene.skills.effects :as skills.effects]
    [enion-cljs.scene.states :as st :refer [player get-model-entity get-player-entity]]))

(def key->skill)

(def idle-run-states #{"idle" "run"})

(def skills-char-cant-run
  #{"hide"
    "attackRange"
    "attackSingle"
    "teleport"
    "breakDefense"
    "heal"
    "cure"})

;; TODO eger karakter stateti idle ve run degilse, ve belirlenen sureden fazla o statete kalmissa duzenleme yap
;; networkten gelen koddan dolayi sikinti olabilir
(def common-states
  [{:anim-state "idle" :event "onIdleStart"}
   {:anim-state "run"
    :event "onRunStart"
    :f (fn [_]
         (when (j/get player :slow-down?)
           (pc/update-anim-speed (get-model-entity) "run" 0.5)
           (j/assoc! player :speed 350))
         (when (j/get player :fleet-foot?)
           (pc/update-anim-speed (get-model-entity) "run" 1.5)))}
   {:anim-state "jump" :event "onJumpEnd" :end? true}
   {:anim-state "jump" :event "onJumpStart" :f (fn [player-entity _]
                                                 (pc/apply-impulse player-entity 0 200 0))}])

(def fleet-food-required-mana (-> common.skills/skills (get "fleetFoot") :required-mana))
(def attack-r-required-mana (-> common.skills/skills (get "attackR") :required-mana))

(defmulti skill-response #(-> % :skill :skill))

(defmethod net/dispatch-pro-response :skill [params]
  (if-let [error (-> params :skill :error)]
    (fire :ui-send-msg (hash-map error true))
    (skill-response params)))

(defn can-skill-be-cancelled? [anim-state active-state state]
  (and (= active-state anim-state)
       (not (j/get state :skill-locked?))))

(defn cancel-skill [anim-state]
  (let [model-entity (get-model-entity)]
    (pc/set-anim-boolean model-entity anim-state false)
    (pc/set-anim-boolean model-entity "run" true)))

(defn skill-pressed? [e skill]
  (= (key->skill (j/get e :key)) skill))

(defn idle? [active-state]
  (and (= "idle" active-state) (k/pressing-wasd?)))

(defn jump? [e active-state]
  (and (idle-run-states active-state)
       (pc/key? e :KEY_SPACE) (j/get player :on-ground?)))

(let [too-far-msg {:too-far true}]
  (defn close-for-attack? [selected-player-id]
    (let [result (<= (st/distance-to selected-player-id) common.skills/close-attack-distance-threshold)]
      (when-not result
        (fire :ui-send-msg too-far-msg))
      result)))

(defn attack-r? [e active-state selected-player-id]
  (and
    (idle-run-states active-state)
    (skill-pressed? e "attackR")
    (st/enemy-selected? selected-player-id)
    (st/alive? selected-player-id)
    (st/enough-mana? attack-r-required-mana)
    (close-for-attack? selected-player-id)))

(defn r-combo? [e prev-state active-state selected-player-id]
  (and (= active-state prev-state)
       (skill-pressed? e "attackR")
       (j/get player :can-r-attack-interrupt?)
       (st/enemy-selected? selected-player-id)
       (st/alive? selected-player-id)
       (close-for-attack? selected-player-id)))

(defn fleet-foot? [e]
  (and
    (skill-pressed? e "fleetFoot")
    (st/cooldown-ready? "fleetFoot")
    (st/enough-mana? fleet-food-required-mana)
    (not (j/get player :fleet-foot?))
    (not (j/get player :slow-down?))))

(defn hp-potion? [e]
  (and
    (skill-pressed? e "hpPotion")
    (st/cooldown-ready? "hpPotion")
    (st/cooldown-ready? "mpPotion")))

(defn mp-potion? [e]
  (and
    (skill-pressed? e "mpPotion")
    (st/cooldown-ready? "hpPotion")
    (st/cooldown-ready? "mpPotion")))

(let [hp-recover {:hp true}]
  (defmethod skill-response "hpPotion" [_]
    (fire :ui-cooldown "hpPotion")
    (fire :ui-send-msg hp-recover)
    (skills.effects/apply-effect-hp-potion player)))

(let [mp-recover {:mp true}]
  (defmethod skill-response "mpPotion" [_]
    (fire :ui-cooldown "mpPotion")
    (fire :ui-send-msg mp-recover)
    (skills.effects/apply-effect-mp-potion player)))

(defmethod skill-response "fleetFoot" [_]
  (fire :ui-cooldown "fleetFoot")
  (skills.effects/apply-effect-fleet-foot player)
  (j/assoc! player
            :fleet-foot? true
            :speed (if (st/asas?) 850 700)))

(defn register-skill-events [events]
  (let [player-entity (get-player-entity)
        model-entity (get-model-entity)]
    (doseq [{:keys [anim-state
                    event
                    skill?
                    call?
                    end?
                    r-lock?
                    r-release?
                    f
                    call-name]} events]
      (pc/on-anim model-entity event
                  (fn []
                    (when f
                      (f player-entity))
                    (when end?
                      (pc/set-anim-boolean model-entity anim-state false)
                      (when (k/pressing-wasd?)
                        (pc/set-anim-boolean model-entity "run" true))
                      (when skill?
                        (j/assoc! player
                                  :skill-locked? false
                                  :can-r-attack-interrupt? false))
                      (when-let [target (and (skills-char-cant-run anim-state)
                                             (j/get player :target-pos-available?)
                                             (j/get player :target-pos))]
                        (pc/look-at model-entity (j/get target :x) (j/get (pc/get-pos model-entity) :y) (j/get target :z) true)))
                    (cond
                      call? (let [selected-player-id (j/get-in player [:skill->selected-player-id anim-state])]
                              (j/assoc! player :skill-locked? true)
                              (j/assoc-in! player [:skill->selected-player-id anim-state] nil)

                              (dispatch-pro :skill (cond-> {:skill anim-state}
                                                     selected-player-id (assoc :selected-player-id (js/parseInt selected-player-id)))))
                      r-release? (j/assoc! player :can-r-attack-interrupt? true)
                      r-lock? (j/assoc! player :can-r-attack-interrupt? false)))))))

(defn register-key->skills [skill-mapping]
  (let [m (reduce-kv (fn [acc k v]
                       (assoc acc (pc/get-code (keyword (str "KEY_" k))) v))
                     {(pc/get-code :KEY_R) "attackR"}
                     skill-mapping)]
    (set! key->skill m)))

(defn char-cant-run? []
  (skills-char-cant-run (pc/get-anim-state (get-model-entity))))
