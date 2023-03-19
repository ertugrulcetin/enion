(ns enion-backend.routes.priest
  (:require
    [common.enion.skills :as common.skills]
    [enion-backend.routes.home :refer :all]
    [enion-backend.teatime :as tea]))

(defn- validate-priest-heal-or-cure [{:keys [ping
                                             id
                                             selected-player-id
                                             world-state
                                             other-player-world-state
                                             skill
                                             player]}]
  (cond
    (ping-too-high? ping) ping-high
    (not (priest? id)) skill-failed
    (not (ally? id selected-player-id)) skill-failed
    (not (alive? world-state)) skill-failed
    (not (alive? other-player-world-state)) skill-failed
    (not (enough-mana? skill world-state)) not-enough-mana
    (not (cooldown-finished? skill player)) skill-failed
    (and (not= id selected-player-id)
         (not (close-for-priest-skills? world-state other-player-world-state))) too-far))

(defmethod apply-skill "heal" [{:keys [id ping current-players current-world]
                                {:keys [skill selected-player-id]} :data}]
  (when-let [player (get current-players id)]
    (when-let [world-state (get current-world id)]
      (when-let [other-player-world-state (get current-world selected-player-id)]
        (if-let [err (validate-priest-heal-or-cure {:id id
                                                    :ping ping
                                                    :selected-player-id selected-player-id
                                                    :world-state world-state
                                                    :other-player-world-state other-player-world-state
                                                    :skill skill
                                                    :player player})]
          err
          (let [required-mana (get-required-mana skill)
                health (get other-player-world-state :health)
                total-health (get-in current-players [selected-player-id :health])
                health-after-heal (+ health (-> common.skills/skills (get skill) :hp))
                health-after-heal (Math/min ^long health-after-heal total-health)]
            (swap! world (fn [world]
                           (-> world
                               (update-in [id :mana] - required-mana)
                               (assoc-in [selected-player-id :health] health-after-heal))))
            (add-effect :heal selected-player-id)
            (when-not (= id selected-player-id)
              (send! selected-player-id :got-heal true))
            (swap! players assoc-in [id :last-time :skill skill] (now))
            {:skill skill
             :selected-player-id selected-player-id}))))))

(defmethod apply-skill "cure" [{:keys [id ping current-players current-world]
                                {:keys [skill selected-player-id]} :data}]
  (when-let [player (get current-players id)]
    (when-let [world-state (get current-world id)]
      (when-let [other-player-world-state (get current-world selected-player-id)]
        (if-let [err (validate-priest-heal-or-cure {:id id
                                                    :ping ping
                                                    :selected-player-id selected-player-id
                                                    :world-state world-state
                                                    :other-player-world-state other-player-world-state
                                                    :skill skill
                                                    :player player})]
          err
          (let [required-mana (get-required-mana skill)
                _ (when-let [task (get-in current-players [selected-player-id :effects :break-defense :task])]
                    (tea/cancel! task))
                _ (swap! players (fn [players]
                                   (-> players
                                       (assoc-in [selected-player-id :effects :break-defense :result] false)
                                       (assoc-in [selected-player-id :effects :break-defense :task] nil))))]
            (swap! world update-in [id :mana] - required-mana)
            (add-effect :cure selected-player-id)
            (when-not (= id selected-player-id)
              (send! selected-player-id :got-cure true))
            (swap! players assoc-in [id :last-time :skill skill] (now))
            {:skill skill
             :selected-player-id selected-player-id}))))))

(defmethod apply-skill "breakDefense" [{:keys [id ping current-players current-world]
                                        {:keys [skill selected-player-id]} :data}]
  (when-let [player (get current-players id)]
    (when-let [world-state (get current-world id)]
      (when-let [other-player-world-state (get current-world selected-player-id)]
        (cond
          (ping-too-high? ping) ping-high
          (not (priest? id)) skill-failed
          (not (enemy? id selected-player-id)) skill-failed
          (not (alive? world-state)) skill-failed
          (not (alive? other-player-world-state)) skill-failed
          (not (enough-mana? skill world-state)) not-enough-mana
          (not (cooldown-finished? skill player)) skill-failed
          (not (close-for-priest-skills? world-state other-player-world-state)) too-far
          :else
          (let [required-mana (get-required-mana skill)
                _ (when-let [task (get-in current-players [selected-player-id :effects :break-defense :task])]
                    (tea/cancel! task))
                _ (when-let [task (get-in current-players [selected-player-id :effects :shield-wall :task])]
                    (tea/cancel! task))
                tea (tea/after! (-> common.skills/skills (get skill) :effect-duration (/ 1000))
                                (bound-fn []
                                  (when (get @players selected-player-id)
                                    (swap! players (fn [players]
                                                     (-> players
                                                         (assoc-in [selected-player-id :effects :break-defense :result] false)
                                                         (assoc-in [selected-player-id :effects :break-defense :task] nil)
                                                         (assoc-in [selected-player-id :effects :shield-wall :result] false)
                                                         (assoc-in [selected-player-id :effects :shield-wall :task] nil))))
                                    (send! selected-player-id :cured-defense-break true))))]
            (swap! players (fn [players]
                             (-> players
                                 (assoc-in [selected-player-id :effects :break-defense :result] true)
                                 (assoc-in [selected-player-id :effects :break-defense :task] tea)
                                 (assoc-in [id :last-time :skill skill] (now)))))
            (swap! world update-in [id :mana] - required-mana)
            (add-effect :break-defense selected-player-id)
            (send! selected-player-id :got-defense-break true)
            {:skill skill
             :selected-player-id selected-player-id}))))))

(defmethod apply-skill "attackPriest" [{:keys [id ping current-players current-world]
                                        {:keys [skill selected-player-id]} :data}]
  (when-let [player (get current-players id)]
    (when (get current-players selected-player-id)
      (let [player-world-state (get current-world id)
            other-player-world-state (get current-world selected-player-id)]
        (cond
          (ping-too-high? ping) ping-high
          (not (priest? id)) skill-failed
          (not (enemy? id selected-player-id)) skill-failed
          (not (alive? player-world-state)) skill-failed
          (not (alive? other-player-world-state)) skill-failed
          (not (enough-mana? skill player-world-state)) not-enough-mana
          (not (cooldown-finished? skill player)) skill-failed
          (not (close-for-attack? player-world-state other-player-world-state)) too-far
          :else (let [required-mana (get-required-mana skill)
                      damage ((-> common.skills/skills (get skill) :damage-fn)
                              (has-defense? selected-player-id)
                              (has-break-defense? selected-player-id))
                      health-after-damage (- (:health other-player-world-state) damage)
                      health-after-damage (Math/max ^long health-after-damage 0)]
                  (swap! world (fn [world]
                                 (-> world
                                     (update-in [id :mana] - required-mana)
                                     (assoc-in [selected-player-id :health] health-after-damage))))
                  (add-effect :attack-priest selected-player-id)
                  (make-asas-appear-if-hidden selected-player-id)
                  (swap! players assoc-in [id :last-time :skill skill] (now))
                  (process-if-enemy-died id selected-player-id health-after-damage current-players)
                  (send! selected-player-id :got-attack-priest-damage {:damage damage
                                                                       :player-id id})
                  {:skill skill
                   :damage damage
                   :selected-player-id selected-player-id}))))))
