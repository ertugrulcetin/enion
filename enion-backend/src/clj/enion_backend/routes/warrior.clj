(ns enion-backend.routes.warrior
  (:require
    [common.enion.skills :as common.skills]
    [enion-backend.routes.home :refer :all]
    [enion-backend.teatime :as tea]
    [enion-backend.utils :as utils]))

(defn- validate-warrior-attack-skill [{:keys [id
                                              ping
                                              selected-player-id
                                              player-world-state
                                              other-player-world-state
                                              npc-world-state
                                              skill
                                              player]}]
  (cond
    (ping-too-high? ping) ping-high
    (not (warrior? id)) skill-failed
    (and (nil? npc-world-state)
         (not (enemy? id selected-player-id))) skill-failed
    (not (alive? player-world-state)) skill-failed
    (and other-player-world-state
         (not (alive? other-player-world-state))) skill-failed
    (and npc-world-state
         (not (alive? npc-world-state))) skill-failed
    (not (enough-mana? skill player-world-state)) not-enough-mana
    (not (satisfies-level? skill player)) skill-failed
    (not (cooldown-finished? skill player)) skill-failed
    (and other-player-world-state
         (not (close-for-attack? player-world-state other-player-world-state))) too-far
    (and npc-world-state
         (not (close-for-attack-to-npc? player-world-state npc-world-state))) too-far))

(defn- validate-warrior-skill [{:keys [id
                                       ping
                                       world-state
                                       skill
                                       player]}]
  (cond
    (ping-too-high? ping) ping-high
    (not (warrior? id)) skill-failed
    (not (alive? world-state)) skill-failed
    (not (satisfies-level? skill player)) skill-failed
    (not (enough-mana? skill world-state)) not-enough-mana
    (not (cooldown-finished? skill player)) skill-failed))

(defmethod apply-skill "attackOneHand" [{:keys [id ping current-players current-world]
                                         {:keys [skill selected-player-id npc?]} :data}]
  (when-let [player (get current-players id)]
    (if npc?
      (attack-to-npc {:id id
                      :selected-player-id selected-player-id
                      :current-world current-world
                      :effect :attack-one-hand
                      :skill skill
                      :player player
                      :ping ping
                      :attack-power (get-attack-power player)
                      :validate-attack-skill-fn validate-warrior-attack-skill})
      (when (get current-players selected-player-id)
        (let [player-world-state (get current-world id)
              other-player-world-state (get current-world selected-player-id)]
          (if-let [err (validate-warrior-attack-skill {:id id
                                                       :ping ping
                                                       :selected-player-id selected-player-id
                                                       :player-world-state player-world-state
                                                       :other-player-world-state other-player-world-state
                                                       :skill skill
                                                       :player player})]
            err
            (let [_ (update-last-combat-time id selected-player-id)
                  required-mana (get-required-mana skill)
                  attack-power (get-attack-power player)
                  damage ((-> common.skills/skills (get skill) :damage-fn)
                          (has-defense? selected-player-id)
                          (has-break-defense? selected-player-id)
                          attack-power)
                  damage (increase-damage-if-has-battle-fury damage current-players id)
                  health-after-damage (- (:health other-player-world-state) damage)
                  health-after-damage (Math/max ^long health-after-damage 0)]
              (swap! world (fn [world]
                             (-> world
                                 (update-in [id :mana] - required-mana)
                                 (assoc-in [selected-player-id :health] health-after-damage))))
              (add-effect :attack-one-hand selected-player-id)
              (make-asas-appear-if-hidden selected-player-id)
              (swap! players assoc-in [id :last-time :skill skill] (now))
              (process-if-enemy-died id selected-player-id health-after-damage current-players)
              (send! selected-player-id :got-attack-one-hand-damage {:damage damage
                                                                     :player-id id})
              {:skill skill
               :damage damage
               :selected-player-id selected-player-id})))))))

(defmethod apply-skill "attackSlowDown" [{:keys [id ping current-players current-world]
                                          {:keys [skill selected-player-id npc?]} :data}]
  (when-let [player (get current-players id)]
    (if npc?
      (attack-to-npc {:id id
                      :selected-player-id selected-player-id
                      :current-world current-world
                      :effect :attack-slow-down
                      :skill skill
                      :player player
                      :attack-power (get-attack-power player)
                      :ping ping
                      :slow-down? (utils/prob? 0.5)
                      :validate-attack-skill-fn validate-warrior-attack-skill})
      (when (get current-players selected-player-id)
        (let [player-world-state (get current-world id)
              other-player-world-state (get current-world selected-player-id)]
          (if-let [err (validate-warrior-attack-skill {:id id
                                                       :ping ping
                                                       :selected-player-id selected-player-id
                                                       :player-world-state player-world-state
                                                       :other-player-world-state other-player-world-state
                                                       :skill skill
                                                       :player player})]
            err
            (let [_ (update-last-combat-time id selected-player-id)
                  required-mana (get-required-mana skill)
                  attack-power (get-attack-power player)
                  damage ((-> common.skills/skills (get skill) :damage-fn)
                          (has-defense? selected-player-id)
                          (has-break-defense? selected-player-id)
                          attack-power)
                  damage (increase-damage-if-has-battle-fury damage current-players id)
                  health-after-damage (- (:health other-player-world-state) damage)
                  health-after-damage (Math/max ^long health-after-damage 0)
                  slow-down? (utils/prob? 0.5)]
              (swap! world (fn [world]
                             (-> world
                                 (update-in [id :mana] - required-mana)
                                 (assoc-in [selected-player-id :health] health-after-damage))))
              (swap! players assoc-in [id :last-time :skill skill] (now))
              (process-if-enemy-died id selected-player-id health-after-damage current-players)
              ;; TODO add scheduler for prob cure
              (send! selected-player-id :got-attack-slow-down-damage {:damage damage
                                                                      :player-id id
                                                                      :slow-down? slow-down?})
              (add-effect :attack-slow-down selected-player-id)
              (make-asas-appear-if-hidden selected-player-id)
              (when slow-down?
                (swap! players assoc-in [selected-player-id :last-time :skill "fleetFoot"] nil)
                (when-let [task (get-in @players [selected-player-id :effects :slow-down :task])]
                  (tea/cancel! task))
                (let [tea (tea/after! (-> common.skills/skills (get skill) :effect-duration (/ 1000))
                                      (bound-fn []
                                        (when (get @players selected-player-id)
                                          (swap! players (fn [players]
                                                           (-> players
                                                               (assoc-in [selected-player-id :effects :slow-down :result] false)
                                                               (assoc-in [selected-player-id :effects :slow-down :task] nil))))
                                          (send! selected-player-id :cured-attack-slow-down-damage true))))]
                  (swap! players (fn [players]
                                   (-> players
                                       (assoc-in [selected-player-id :effects :slow-down :result] true)
                                       (assoc-in [selected-player-id :effects :slow-down :task] tea))))))
              {:skill skill
               :damage damage
               :selected-player-id selected-player-id})))))))

(defmethod apply-skill "shieldWall" [{:keys [id ping current-players current-world]
                                      {:keys [skill]} :data}]
  (when-let [player (get current-players id)]
    (when-let [world-state (get current-world id)]
      (if-let [err (validate-warrior-skill {:id id
                                            :ping ping
                                            :world-state world-state
                                            :skill skill
                                            :player player})]
        err
        (let [required-mana (get-required-mana skill)
              _ (when-let [task (get-in current-players [id :effects :shield-wall :task])]
                  (tea/cancel! task))
              tea (tea/after! (-> common.skills/skills (get skill) :effect-duration (/ 1000))
                              (bound-fn []
                                (when (get @players id)
                                  (send! id :shield-wall-finished true)
                                  (swap! players (fn [players]
                                                   (-> players
                                                       (assoc-in [id :effects :shield-wall :result] false)
                                                       (assoc-in [id :effects :shield-wall :task] nil)))))))]
          ;; TODO update here, after implementing party system
          (swap! world update-in [id :mana] - required-mana)
          (swap! players (fn [players]
                           (-> players
                               (assoc-in [id :last-time :skill skill] (now))
                               (assoc-in [id :effects :shield-wall :result] true)
                               (assoc-in [id :effects :shield-wall :task] tea))))
          {:skill skill})))))

(defmethod apply-skill "battleFury" [{:keys [id ping current-players current-world]
                                      {:keys [skill]} :data}]
  (when-let [player (get current-players id)]
    (when-let [world-state (get current-world id)]
      (if-let [err (validate-warrior-skill {:id id
                                            :ping ping
                                            :world-state world-state
                                            :skill skill
                                            :player player})]
        err
        (let [required-mana (get-required-mana skill)
              _ (when-let [task (get-in current-players [id :effects :battle-fury :task])]
                  (tea/cancel! task))
              tea (tea/after! (-> common.skills/skills (get skill) :effect-duration (/ 1000))
                              (bound-fn []
                                (when (get @players id)
                                  (send! id :shield-wall-finished true)
                                  (swap! players (fn [players]
                                                   (-> players
                                                       (assoc-in [id :effects :battle-fury :result] false)
                                                       (assoc-in [id :effects :battle-fury :task] nil)))))))]
          ;; TODO update here, after implementing party system
          (swap! world update-in [id :mana] - required-mana)
          (swap! players (fn [players]
                           (-> players
                               (assoc-in [id :last-time :skill skill] (now))
                               (assoc-in [id :effects :battle-fury :result] true)
                               (assoc-in [id :effects :battle-fury :task] tea))))
          {:skill skill})))))
