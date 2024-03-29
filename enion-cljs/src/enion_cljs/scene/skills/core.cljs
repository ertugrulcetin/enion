(ns enion-cljs.scene.skills.core
  (:require
    [applied-science.js-interop :as j]
    [common.enion.skills :as common.skills]
    [enion-cljs.common :as common :refer [dlog fire]]
    [enion-cljs.scene.entities.camera :as entity.camera]
    [enion-cljs.scene.keyboard :as k]
    [enion-cljs.scene.network :as net :refer [dispatch-pro]]
    [enion-cljs.scene.pc :as pc]
    [enion-cljs.scene.skills.effects :as skills.effects]
    [enion-cljs.scene.states :as st :refer [player get-model-entity get-player-entity]]
    [enion-cljs.scene.utils :as utils]))

(def key->skill)
(def hp-potion-key-code)

(def idle-run-states #{"idle" "run"})

(def skills-char-cant-run
  #{"hide"
    "attackRange"
    "attackSingle"
    "attackIce"
    "teleport"
    "breakDefense"
    "heal"
    "cure"})

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
                                                 (st/play-sound "jump")
                                                 (pc/apply-impulse player-entity 0 200 0))}])

(defn get-required-mana [skill]
  (-> common.skills/skills (get skill) :required-mana))

(def fleet-food-required-mana (get-required-mana "fleetFoot"))
(def attack-r-required-mana (get-required-mana "attackR"))

(defmulti skill-response #(-> % :skill :skill))

(defmethod net/dispatch-pro-response :skill [params]
  (if-let [error (-> params :skill :error)]
    (fire :show-text (hash-map error true))
    (skill-response params)))

(defn can-skill-be-cancelled? [anim-state active-state state]
  (and (= active-state anim-state)
       (not (j/get state :skill-locked?))))

(defn cancel-skill [anim-state]
  (let [model-entity (get-model-entity)]
    (pc/set-anim-boolean model-entity anim-state false)
    (pc/set-anim-boolean model-entity "run" true)
    (st/stop-sound anim-state)))

(defn skill-pressed? [e skill]
  (= (key->skill (j/get e :key)) skill))

(defn run? [active-state]
  (and (= "idle" active-state) (k/pressing-wasd?)))

(defn jump? [e active-state]
  (and (idle-run-states active-state)
       (pc/key? e :KEY_SPACE)
       (j/get player :on-ground?)
       (not (j/get st/settings :tutorial?))
       (not (j/get player :slow-down?))))

(let [too-far {:too-far true}]
  (defn close-for-attack? [selected-player-id]
    (let [result (<= (st/distance-to selected-player-id) common.skills/close-attack-distance-threshold)]
      (when-not result
        (fire :show-text too-far))
      result)))

(defn close-for-r-attack? [selected-player-id]
  (<= (st/distance-to selected-player-id) common.skills/close-attack-distance-threshold))

(defn can-attack-to-enemy? [selected-player-id]
  (let [npc? (st/npc-selected?)]
    (and
      (st/enemy-selected? selected-player-id npc?)
      (st/alive? selected-player-id npc?))))

(defn- get-hidden-enemy-asases []
  (reduce
    (fn [acc id]
      (let [other-player (j/get st/other-players id)]
        (if (and (j/get other-player :enemy?) (j/get other-player :hide?))
          (conj acc other-player)
          acc)))
    [] (js/Object.keys st/other-players)))

(defn enable-phantom-vision []
  (j/assoc! player :phantom-vision? true)
  (doseq [a (get-hidden-enemy-asases)]
    (j/call-in a [:skills :hide])))

(defn disable-phantom-vision []
  (j/assoc! player :phantom-vision? false)
  (doseq [a (get-hidden-enemy-asases)]
    (j/call-in a [:skills :hide])))

(defn satisfies-level? [skill player]
  (>= (j/get player :level) (get-in common.skills/skills [skill :required-level])))

(defn attack-r? [e active-state selected-player-id]
  (and
    (idle-run-states active-state)
    (skill-pressed? e "attackR")
    (can-attack-to-enemy? selected-player-id)
    (st/enough-mana? attack-r-required-mana)
    (close-for-r-attack? selected-player-id)))

(defn r-combo? [e prev-state active-state selected-player-id]
  (and (= active-state prev-state)
       (skill-pressed? e "attackR")
       (j/get player :can-r-attack-interrupt?)
       (can-attack-to-enemy? selected-player-id)
       (close-for-attack? selected-player-id)))

(defn fleet-foot? [e]
  (and
    (satisfies-level? "fleetFoot" player)
    (skill-pressed? e "fleetFoot")
    (st/cooldown-ready? "fleetFoot")
    (st/enough-mana? fleet-food-required-mana)
    (not (j/get player :fleet-foot?))
    (not (j/get player :slow-down?))))

(defn- enough-hp-potions? []
  (> (j/get player :hp-potions) 0))

(defn- enough-mp-potions? []
  (> (j/get player :mp-potions) 0))

(defn hp-potion? [e]
  (and
    (skill-pressed? e "hpPotion")
    (st/cooldown-ready? "hpPotion")
    (st/cooldown-ready? "mpPotion")
    (enough-hp-potions?)))

(defn mp-potion? [e]
  (and
    (skill-pressed? e "mpPotion")
    (st/cooldown-ready? "hpPotion")
    (st/cooldown-ready? "mpPotion")
    (enough-mp-potions?)))

(defn use-hp-potion []
  (let [hp-potions (j/get (j/update! st/player :hp-potions dec) :hp-potions)]
    (fire :ui-update-hp-potions hp-potions)
    (utils/set-item "potions" (pr-str {:hp-potions hp-potions
                                       :mp-potions (j/get st/player :mp-potions)}))))

(defn use-mp-potion []
  (let [mp-potions (j/get (j/update! st/player :mp-potions dec) :mp-potions)]
    (fire :ui-update-mp-potions mp-potions)
    (utils/set-item "potions" (pr-str {:hp-potions (j/get st/player :hp-potions)
                                       :mp-potions mp-potions}))))

(let [hp-recover {:hp (-> common.skills/skills (get "hpPotion") :hp)}]
  (defmethod skill-response "hpPotion" [_]
    (fire :ui-cooldown "hpPotion")
    (fire :show-text hp-recover)
    (skills.effects/apply-effect-hp-potion player)
    (st/play-sound "potion")
    (use-hp-potion)))

(let [mp-recover {:mp (-> common.skills/skills (get "mpPotion") :mp)}]
  (defmethod skill-response "mpPotion" [_]
    (fire :ui-cooldown "mpPotion")
    (fire :show-text mp-recover)
    (skills.effects/apply-effect-mp-potion player)
    (st/play-sound "potion")
    (use-mp-potion)))

(defmethod skill-response "fleetFoot" [_]
  (fire :ui-cooldown "fleetFoot")
  (skills.effects/apply-effect-fleet-foot player)
  (j/assoc! player
            :fleet-foot? true
            :speed (if (st/asas?) st/speed-fleet-foot-asas st/speed-fleet-foot))
  (st/play-sound "fleetFoot")
  (fire :show-text {:mp-used fleet-food-required-mana}))

(defmethod skill-response "attackR" [params]
  (fire :ui-cooldown "attackR")
  (let [damage (-> params :skill :damage)]
    (fire :show-text {:hit damage})
    (st/play-sound "attackR")))

(defmethod skill-response "baseDamage" [params]
  (let [damage (-> params :skill :damage)]
    (skills.effects/apply-effect-attack-cauldron player)
    (fire :show-text {:damage damage})
    (st/play-sound "attackR")))

(defmethod skill-response "npcDamage" [params]
  (let [{:keys [damage exp lost-exp]} (:skill params)]
    (skills.effects/apply-effect-attack-cauldron player)
    (fire :show-text {:damage damage})
    (when lost-exp
      (fire :show-text {:lost-exp lost-exp})
      (fire :ui-set-exp exp))
    (st/play-sound "attackR")))

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
                    f]} events]
      (pc/on-anim model-entity event
                  (fn []
                    (when f
                      (f player-entity))
                    (when end?
                      (pc/set-anim-boolean model-entity anim-state false)
                      (st/process-running)
                      (when skill?
                        (j/assoc! player
                                  :skill-locked? false
                                  :can-r-attack-interrupt? false))
                      (when-let [target (and (skills-char-cant-run anim-state)
                                             (j/get player :target-pos-available?)
                                             (j/get player :target-pos))]
                        (pc/look-at model-entity (j/get target :x) (j/get (pc/get-pos model-entity) :y) (j/get target :z) true)))
                    (cond
                      call? (let [selected-player-id (j/get-in player [:skill->selected-player-id anim-state])
                                  npc? (j/get-in player [:skill->selected-enemy-npc? anim-state])
                                  nova-pos (j/get player :nova-pos)]
                              (j/assoc! player :skill-locked? true)
                              (j/assoc-in! player [:skill->selected-player-id anim-state] nil)
                              (j/assoc-in! player [:skill->selected-enemy-npc? anim-state] nil)
                              (j/assoc! player :nova-pos nil)

                              (dispatch-pro :skill
                                            (cond-> {:skill anim-state}
                                              selected-player-id (assoc :selected-player-id (js/parseInt selected-player-id))
                                              npc? (assoc :npc? true)
                                              nova-pos (assoc :x (j/get nova-pos :x)
                                                              :y (j/get nova-pos :y)
                                                              :z (j/get nova-pos :z)))))
                      r-release? (j/assoc! player :can-r-attack-interrupt? true)
                      r-lock? (j/assoc! player :can-r-attack-interrupt? false)))))))

(defn- find-hp-potion-key-code [key->skill]
  (some
    (fn [[k v]]
      (when (= v "hpPotion")
        k))
    key->skill))

(defn register-key->skills [skill-mapping]
  (let [m (reduce-kv (fn [acc k v]
                       (assoc acc (pc/get-code (keyword (str "KEY_" k))) v))
                     {(pc/get-code :KEY_R) "attackR"}
                     skill-mapping)]
    (set! key->skill m)
    (set! hp-potion-key-code (find-hp-potion-key-code m))))

(defn char-cant-run? []
  (skills-char-cant-run (pc/get-anim-state (get-model-entity))))

(defmethod net/dispatch-pro-response :got-attack-one-hand-damage [params]
  (let [params (:got-attack-one-hand-damage params)
        damage (:damage params)]
    (skills.effects/apply-effect-attack-one-hand st/player)
    (fire :show-text {:damage damage})))

(defmethod net/dispatch-pro-response :got-attack-slow-down-damage [params]
  (let [params (:got-attack-slow-down-damage params)
        damage (:damage params)
        slow-down? (:slow-down? params)]
    (when slow-down?
      (j/assoc! st/player
                :slow-down? true
                :fleet-foot? false)
      (j/assoc-in! (st/get-player-entity) [:c :sound :slots :run_2 :pitch] 0)
      (fire :ui-slow-down? true)
      (fire :ui-cancel-skill "fleetFoot"))
    (skills.effects/apply-effect-attack-slow-down st/player)
    (fire :show-text {:damage damage})))

(defmethod net/dispatch-pro-response :got-attack-dagger-damage [params]
  (let [params (:got-attack-dagger-damage params)
        damage (:damage params)]
    (skills.effects/apply-effect-attack-dagger st/player)
    (fire :show-text {:damage damage})))

(defmethod net/dispatch-pro-response :got-attack-stab-damage [params]
  (let [params (:got-attack-stab-damage params)
        damage (:damage params)]
    (skills.effects/apply-effect-attack-stab st/player)
    (fire :show-text {:damage damage})))

(defmethod net/dispatch-pro-response :cured-attack-slow-down-damage [_]
  (pc/update-anim-speed (st/get-model-entity) "run" 1)
  (j/assoc! st/player
            :slow-down? false
            :speed st/speed)
  (j/assoc-in! (st/get-player-entity) [:c :sound :slots :run_2 :pitch] 1)
  (fire :ui-slow-down? false)
  (fire :ui-cancel-skill "fleetFoot")
  (st/set-cooldown true "fleetFoot"))

(defmethod net/dispatch-pro-response :got-attack-r-damage [params]
  (let [params (:got-attack-r-damage params)
        damage (:damage params)]
    (skills.effects/apply-effect-attack-r st/player)
    (fire :show-text {:damage damage})))

(defmethod net/dispatch-pro-response :fleet-foot-finished [_]
  (dlog "fleetFood finished")
  (j/assoc! st/player
            :fleet-foot? false
            :speed st/speed)
  (pc/update-anim-speed (st/get-model-entity) "run" 1))

(defmethod net/dispatch-pro-response :phantom-vision-finished [_]
  (dlog "phantom vision finished")
  (j/assoc! st/player :phantom-vision? false))

(defmethod net/dispatch-pro-response :shield-wall-finished [_]
  (dlog "shield wall finished"))

(let [heal-msg {:hp (-> common.skills/skills (get "heal") :hp)}]
  (defmethod net/dispatch-pro-response :got-heal [_]
    (skills.effects/add-to-healed-ids)
    (fire :show-text heal-msg)))

(let [cure-msg {:cure true}]
  (defmethod net/dispatch-pro-response :got-cure [_]
    (skills.effects/apply-effect-got-cure st/player)
    (fire :show-text cure-msg)
    (fire :ui-cured)))

(let [defense-break-msg {:defense-break true}]
  (defmethod net/dispatch-pro-response :got-defense-break [_]
    (skills.effects/apply-effect-got-defense-break st/player)
    (fire :show-text defense-break-msg)
    (fire :ui-got-defense-break)))

(defmethod net/dispatch-pro-response :got-attack-priest-damage [params]
  (let [params (:got-attack-priest-damage params)
        damage (:damage params)]
    (skills.effects/apply-effect-attack-priest st/player)
    (fire :show-text {:damage damage})))

;; write for :cured-defense-break-damage
(defmethod net/dispatch-pro-response :cured-defense-break [_]
  (fire :ui-cured))

;; write a function like got-attack-one-hand-damage but for :got-attack-range
(defmethod net/dispatch-pro-response :got-attack-range [params]
  (let [params (:got-attack-range params)
        damage (:damage params)]
    (fire :show-text {:damage damage})
    (entity.camera/shake-camera)))

;; write function for :got-attack-single
(defmethod net/dispatch-pro-response :got-attack-single [params]
  (let [params (:got-attack-single params)
        damage (:damage params)]
    (fire :show-text {:damage damage})
    (skills.effects/apply-effect-attack-flame st/player)))

(defmethod net/dispatch-pro-response :teleported [params]
  (let [params (:teleported params)
        x (:x params)
        y (:y params)
        z (:z params)]
    (j/call-in (st/get-player-entity) [:rigidbody :teleport] x y z)
    (skills.effects/apply-effect-teleport st/player)
    (st/cancel-target-pos)))

(defmethod net/dispatch-pro-response :got-attack-ice [params]
  (let [params (:got-attack-ice params)
        damage (:damage params)
        ice-slow-down? (:ice-slow-down? params)]
    (when ice-slow-down?
      (j/assoc! st/player
                :slow-down? true
                :fleet-foot? false)
      (j/assoc-in! (st/get-player-entity) [:c :sound :slots :run_2 :pitch] 0)
      (fire :ui-slow-down? true)
      (fire :ui-cancel-skill "fleetFoot"))
    (skills.effects/apply-effect-ice-spell st/player)
    (fire :show-text {:damage damage})))

(defmethod net/dispatch-pro-response :cured-attack-ice-damage [_]
  (pc/update-anim-speed (st/get-model-entity) "run" 1)
  (j/assoc! st/player
            :slow-down? false
            :speed st/speed)
  (j/assoc-in! (st/get-player-entity) [:c :sound :slots :run_2 :pitch] 1)
  (fire :ui-slow-down? false)
  (fire :ui-cancel-skill "fleetFoot")
  (st/set-cooldown true "fleetFoot"))
