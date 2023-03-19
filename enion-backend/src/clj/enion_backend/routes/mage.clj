(ns enion-backend.routes.mage
  (:require
    [common.enion.skills :as common.skills]
    [enion-backend.routes.home :refer :all]
    [enion-backend.teatime :as tea]))

(def nova-radius 2.25)

(defn- validate-single-attack [{:keys [id
                                       ping
                                       selected-player-id
                                       world-state
                                       other-player-world-state
                                       skill
                                       player]}]
  (cond
    (ping-too-high? ping) ping-high
    (not (mage? id)) skill-failed
    (not (enemy? id selected-player-id)) skill-failed
    (not (alive? world-state)) skill-failed
    (not (alive? other-player-world-state)) skill-failed
    (not (enough-mana? skill world-state)) not-enough-mana
    (not (cooldown-finished? skill player)) skill-failed
    (not (close-for-attack-single? world-state other-player-world-state)) too-far))

(defmethod apply-skill "attackRange" [{:keys [id ping current-players current-world]
                                       {:keys [skill x y z]} :data}]
  (when-let [player (get current-players id)]
    (when-let [world-state (get current-world id)]
      (cond
        (ping-too-high? ping) ping-high
        (not (mage? id)) skill-failed
        (not (alive? world-state)) skill-failed
        (not (enough-mana? skill world-state)) not-enough-mana
        (not (cooldown-finished? skill player)) skill-failed
        (not (attack-range-in-distance? world-state x y z)) skill-failed
        :else
        (let [required-mana (get-required-mana skill)
              _ (swap! players assoc-in [id :last-time :skill skill] (now))
              _ (swap! world update-in [id :mana] - required-mana)
              _ (add-effect :attack-range {:id id
                                           :x x
                                           :y y
                                           :z z})
              damaged-enemies (doall
                                (for [enemy-id (keys current-players)
                                      :let [enemy-world-state (get current-world enemy-id)]
                                      :when (and enemy-world-state
                                                 (enemy? id enemy-id)
                                                 (alive? enemy-world-state)
                                                 (inside-circle? (:px enemy-world-state) (:pz enemy-world-state) x z nova-radius))]
                                  (let [_ (update-last-combat-time enemy-id)
                                        damage ((-> common.skills/skills (get skill) :damage-fn)
                                                (has-defense? enemy-id)
                                                (has-break-defense? enemy-id))
                                        health-after-damage (- (:health enemy-world-state) damage)
                                        health-after-damage (Math/max ^long health-after-damage 0)]
                                    (make-asas-appear-if-hidden enemy-id)
                                    (swap! world assoc-in [enemy-id :health] health-after-damage)
                                    (process-if-enemy-died id enemy-id health-after-damage current-players)
                                    (send! enemy-id :got-attack-range {:damage damage
                                                                       :player-id id})
                                    {:id enemy-id
                                     :damage damage})))]
          (when (seq damaged-enemies)
            (update-last-combat-time id))
          {:skill skill
           :x x
           :y y
           :z z
           :damages damaged-enemies})))))

(defmethod apply-skill "attackSingle" [{:keys [id ping current-players current-world]
                                        {:keys [skill selected-player-id]} :data}]
  (when-let [player (get current-players id)]
    (when-let [world-state (get current-world id)]
      (when-let [other-player-world-state (get current-world selected-player-id)]
        (if-let [err (validate-single-attack {:id id
                                              :ping ping
                                              :selected-player-id selected-player-id
                                              :world-state world-state
                                              :other-player-world-state other-player-world-state
                                              :skill skill
                                              :player player})]
          err
          (let [_ (update-last-combat-time id selected-player-id)
                required-mana (get-required-mana skill)
                _ (swap! players assoc-in [id :last-time :skill skill] (now))
                _ (swap! world update-in [id :mana] - required-mana)
                damage ((-> common.skills/skills (get skill) :damage-fn)
                        (has-defense? selected-player-id)
                        (has-break-defense? selected-player-id))
                health-after-damage (- (:health other-player-world-state) damage)
                health-after-damage (Math/max ^long health-after-damage 0)]
            (make-asas-appear-if-hidden selected-player-id)
            (process-if-enemy-died id selected-player-id health-after-damage current-players)
            (swap! world assoc-in [selected-player-id :health] health-after-damage)
            (send! selected-player-id :got-attack-single {:damage damage
                                                          :player-id id})
            (add-effect :attack-single selected-player-id)
            {:skill skill
             :selected-player-id selected-player-id
             :damage damage}))))))

(defmethod apply-skill "attackIce" [{:keys [id ping current-players current-world]
                                     {:keys [skill selected-player-id]} :data}]
  (when-let [player (get current-players id)]
    (when-let [world-state (get current-world id)]
      (when-let [other-player-world-state (get current-world selected-player-id)]
        (if-let [err (validate-single-attack {:id id
                                              :ping ping
                                              :selected-player-id selected-player-id
                                              :world-state world-state
                                              :other-player-world-state other-player-world-state
                                              :skill skill
                                              :player player})]
          err
          (let [_ (update-last-combat-time id selected-player-id)
                required-mana (get-required-mana skill)
                _ (swap! players assoc-in [id :last-time :skill skill] (now))
                _ (swap! world update-in [id :mana] - required-mana)
                damage ((-> common.skills/skills (get skill) :damage-fn)
                        (has-defense? selected-player-id)
                        (has-break-defense? selected-player-id))
                health-after-damage (- (:health other-player-world-state) damage)
                health-after-damage (Math/max ^long health-after-damage 0)
                ice-slow-down? (prob? 0.2)]
            (make-asas-appear-if-hidden selected-player-id)
            (process-if-enemy-died id selected-player-id health-after-damage current-players)
            (swap! world assoc-in [selected-player-id :health] health-after-damage)
            (send! selected-player-id :got-attack-ice {:damage damage
                                                       :player-id id
                                                       :ice-slow-down? ice-slow-down?})
            (add-effect :attack-ice selected-player-id)
            (when ice-slow-down?
              (swap! players assoc-in [selected-player-id :last-time :skill "fleetFoot"] nil)
              (when-let [task (get-in current-players [selected-player-id :effects :attack-ice :task])]
                (tea/cancel! task))
              (let [tea (tea/after! (-> common.skills/skills (get skill) :effect-duration (/ 1000))
                                    (bound-fn []
                                      (when (get @players selected-player-id)
                                        (swap! players (fn [players]
                                                         (-> players
                                                             (assoc-in [selected-player-id :effects :attack-ice :result] false)
                                                             (assoc-in [selected-player-id :effects :attack-ice :task] nil))))
                                        (send! selected-player-id :cured-attack-ice-damage true))))]
                (swap! players (fn [players]
                                 (-> players
                                     (assoc-in [selected-player-id :effects :attack-ice :result] true)
                                     (assoc-in [selected-player-id :effects :attack-ice :task] tea))))))
            {:skill skill
             :selected-player-id selected-player-id
             :damage damage}))))))

(defmethod apply-skill "teleport" [{:keys [id ping current-players current-world]
                                    {:keys [skill selected-player-id]} :data}]
  (when-let [player (get current-players id)]
    (when-let [world-state (get current-world id)]
      (when-let [other-player-world-state (get current-world selected-player-id)]
        (cond
          (ping-too-high? ping) ping-high
          (not (mage? id)) skill-failed
          (not (ally? id selected-player-id)) skill-failed
          (not (alive? world-state)) skill-failed
          (not (alive? other-player-world-state)) skill-failed
          (not (enough-mana? skill world-state)) not-enough-mana
          (not (cooldown-finished? skill player)) skill-failed
          (not (already-in-the-party? player (get current-players selected-player-id))) skill-failed
          (= id selected-player-id) skill-failed
          :else (let [required-mana (get-required-mana skill)
                      current-time (now)
                      new-pos {:x (:px world-state)
                               :y (:py world-state)
                               :z (:pz world-state)}
                      _ (send! selected-player-id :teleported new-pos)
                      _ (swap! players (fn [players]
                                         (-> players
                                             (assoc-in [id :last-time :skill skill] current-time)
                                             (assoc-in [selected-player-id :last-time :teleported] current-time))))
                      _ (swap! world update-in [id :mana] - required-mana)]
                  (add-effect :teleport selected-player-id)
                  {:skill skill}))))))
