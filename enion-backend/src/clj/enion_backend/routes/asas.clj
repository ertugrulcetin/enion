(ns enion-backend.routes.asas
  (:require
    [common.enion.skills :as common.skills]
    [enion-backend.routes.home :refer :all]
    [enion-backend.teatime :as tea]))

(defn- validate-asas-attack-skill [{:keys [id
                                           ping
                                           selected-player-id
                                           player-world-state
                                           other-player-world-state
                                           npc-world-state
                                           skill
                                           player]}]
  (cond
    (ping-too-high? ping) ping-high
    (not (asas? id)) skill-failed
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
         (below-level-10? player)) pvp-locked
    (and other-player-world-state
         (enemy-below-level-10? selected-player-id)) enemy-low-level
    (and other-player-world-state
         (not (close-for-attack? player-world-state other-player-world-state))) too-far
    (and npc-world-state
         (not (close-for-attack-to-npc? player-world-state npc-world-state))) too-far))

(defn- validate-asas-skill [{:keys [id
                                    ping
                                    world-state
                                    skill
                                    player]}]
  (cond
    (ping-too-high? ping) ping-high
    (not (asas? id)) skill-failed
    (not (alive? world-state)) skill-failed
    (not (enough-mana? skill world-state)) not-enough-mana
    (not (satisfies-level? skill player)) skill-failed
    (not (cooldown-finished? skill player)) skill-failed))

(defmethod apply-skill "attackDagger" [{:keys [id ping current-players current-world]
                                        {:keys [skill selected-player-id npc?]} :data}]
  (when-let [player (get current-players id)]
    (if npc?
      (attack-to-npc {:id id
                      :selected-player-id selected-player-id
                      :current-world current-world
                      :effect :attack-dagger
                      :skill skill
                      :player player
                      :ping ping
                      :attack-power (get-attack-power player)
                      :validate-attack-skill-fn validate-asas-attack-skill})
      (when (get current-players selected-player-id)
        (let [player-world-state (get current-world id)
              other-player-world-state (get current-world selected-player-id)]
          (if-let [err (validate-asas-attack-skill {:id id
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
                  health-after-damage (- (:health other-player-world-state) damage)
                  health-after-damage (Math/max ^long health-after-damage 0)]
              (swap! world (fn [world]
                             (-> world
                                 (update-in [id :mana] - required-mana)
                                 (assoc-in [selected-player-id :health] health-after-damage))))
              (add-effect :attack-dagger selected-player-id)
              (make-asas-appear-if-hidden id)
              (make-asas-appear-if-hidden selected-player-id)
              (swap! players assoc-in [id :last-time :skill skill] (now))
              (process-if-enemy-died id selected-player-id health-after-damage current-players)
              (send! selected-player-id :got-attack-dagger-damage {:damage damage
                                                                   :player-id id})
              {:skill skill
               :damage damage
               :selected-player-id selected-player-id})))))))

(defmethod apply-skill "attackStab" [{:keys [id ping current-players current-world]
                                      {:keys [skill selected-player-id npc?]} :data}]
  (when-let [player (get current-players id)]
    (if npc?
      (attack-to-npc {:id id
                      :selected-player-id selected-player-id
                      :current-world current-world
                      :effect :attack-stab
                      :skill skill
                      :player player
                      :ping ping
                      :attack-power (get-attack-power player)
                      :validate-attack-skill-fn validate-asas-attack-skill})
      (when (get current-players selected-player-id)
        (let [player-world-state (get current-world id)
              other-player-world-state (get current-world selected-player-id)]
          (if-let [err (validate-asas-attack-skill {:id id
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
                  health-after-damage (- (:health other-player-world-state) damage)
                  health-after-damage (Math/max ^long health-after-damage 0)]
              (swap! world (fn [world]
                             (-> world
                                 (update-in [id :mana] - required-mana)
                                 (assoc-in [selected-player-id :health] health-after-damage))))
              (add-effect :attack-stab selected-player-id)
              (make-asas-appear-if-hidden id)
              (make-asas-appear-if-hidden selected-player-id)
              (swap! players assoc-in [id :last-time :skill skill] (now))
              (process-if-enemy-died id selected-player-id health-after-damage current-players)
              (send! selected-player-id :got-attack-stab-damage {:damage damage
                                                                 :player-id id})
              {:skill skill
               :damage damage
               :selected-player-id selected-player-id})))))))

(defmethod apply-skill "phantomVision" [{:keys [id ping current-players current-world]
                                         {:keys [skill]} :data}]
  (when-let [player (get current-players id)]
    (when-let [world-state (get current-world id)]
      (if-let [err (validate-asas-skill {:id id
                                         :ping ping
                                         :world-state world-state
                                         :skill skill
                                         :player player})]
        err
        (let [required-mana (get-required-mana skill)
              _ (when-let [task (get-in current-players [id :effects :phantom-vision :task])]
                  (tea/cancel! task))
              tea (tea/after! (-> common.skills/skills (get skill) :effect-duration (/ 1000))
                              (bound-fn []
                                (when (get @players id)
                                  (send! id :phantom-vision-finished true)
                                  (swap! players (fn [players]
                                                   (-> players
                                                       (assoc-in [id :effects :phantom-vision :result] false)
                                                       (assoc-in [id :effects :phantom-vision :task] nil)))))))]
          ;; TODO update here, after implementing party system
          (swap! world update-in [id :mana] - required-mana)
          (swap! players (fn [players]
                           (-> players
                               (assoc-in [id :last-time :skill skill] (now))
                               (assoc-in [id :effects :phantom-vision :result] true)
                               (assoc-in [id :effects :phantom-vision :task] tea))))
          {:skill skill})))))

(defmethod apply-skill "hide" [{:keys [id ping current-players current-world]
                                {:keys [skill]} :data}]
  (when-let [player (get current-players id)]
    (when-let [world-state (get current-world id)]
      (if-let [err (validate-asas-skill {:id id
                                         :ping ping
                                         :world-state world-state
                                         :skill skill
                                         :player player})]
        err
        (let [required-mana (get-required-mana skill)
              _ (when-let [task (get-in current-players [id :effects :hide :task])]
                  (tea/cancel! task))
              tea (tea/after! (-> common.skills/skills (get skill) :effect-duration (/ 1000))
                              (bound-fn []
                                (when (get @players id)
                                  (send! id :hide-finished true)
                                  (swap! players assoc-in [id :effects :hide :result] false))))]
          (swap! world update-in [id :mana] - required-mana)
          (swap! players (fn [players]
                           (-> players
                               (assoc-in [id :last-time :skill skill] (now))
                               (assoc-in [id :effects :hide :result] true)
                               (assoc-in [id :effects :hide :task] tea))))
          {:skill skill})))))
