(ns enion-backend.routes.asas
  (:require
    [enion-backend.routes.common :as common]))

;; write the same function like (defmethod apply-skill "attackOneHand"..) but for asas class and for skill attackDagger
;; make sure that we check the player has the class asas
(defmethod common/apply-skill "attackDagger" [{:keys [id ping] {:keys [skill selected-player-id]} :data}]
  (when-let [player (get @players id)]
    (when (get @players selected-player-id)
      (let [w @world
            player-world-state (get w id)
            other-player-world-state (get w selected-player-id)]
        (cond
          (> ping 5000) skill-failed
          (not (asas? id)) skill-failed
          (not (alive? player-world-state)) skill-failed
          (not (alive? other-player-world-state)) skill-failed
          (not (enough-mana? skill player-world-state)) not-enough-mana
          (not (cooldown-finished? skill player)) skill-failed
          (not (close-for-attack? player-world-state other-player-world-state)) too-far
          :else (let [required-mana (get-required-mana skill)
                      ;; TODO update damage, player might have defense or poison etc.
                      damage ((-> common.skills/skills (get skill) :damage-fn) (has-defense? selected-player-id))
                      health-after-damage (- (:health other-player-world-state) damage)
                      health-after-damage (Math/max ^long health-after-damage 0)]
                  (swap! world (fn [world]
                                 (-> world
                                     (update-in [id :mana] - required-mana)
                                     (assoc-in [selected-player-id :health] health-after-damage))))
                  (swap! players assoc-in [id :last-time :skill skill] (now))
                  (when (= 0 health-after-damage)
                    (swap! players assoc-in [id :last-time :died] (now)))
                  (send! selected-player-id :got-attack-dagger-damage {:damage damage
                                                                       :player-id id})
                  {:skill skill
                   :damage damage
                   :selected-player-id selected-player-id}))))))

;; write function like (defmethod apply-skill "fleetFloot"..) but for asas class and for skill phantomVision
(defmethod apply-skill "phantomVision" [{:keys [id ping] {:keys [skill]} :data}]
  (when-let [player (get @players id)]
    (when-let [world-state (get @world id)]
      (cond
        (> ping 5000) skill-failed
        (not (asas? id)) skill-failed
        (not (alive? world-state)) skill-failed
        (not (enough-mana? skill world-state)) not-enough-mana
        (not (cooldown-finished? skill player)) skill-failed
        :else (let [required-mana (get-required-mana skill)]
                ;; TODO apply phantomVision for party members when party system is implemented
                (swap! world update-in [id :mana] - required-mana)
                (swap! players assoc-in [id :last-time :skill skill] (now))
                (tea/after! (-> common.skills/skills (get skill) :effect-duration (/ 1000))
                            (bound-fn []
                              (when (get @players id)
                                (send! id :phantom-vision-finished true))))
                {:skill skill})))))

;; write function like (defmethod apply-skill "phantomVision"..) but for asas class and for skill hide
(defmethod apply-skill "hide" [{:keys [id ping] {:keys [skill]} :data}]
  (when-let [player (get @players id)]
    (when-let [world-state (get @world id)]
      (cond
        (> ping 5000) skill-failed
        (not (asas? id)) skill-failed
        (not (alive? world-state)) skill-failed
        (not (enough-mana? skill world-state)) not-enough-mana
        (not (cooldown-finished? skill player)) skill-failed
        :else (let [required-mana (get-required-mana skill)]
                (swap! world update-in [id :mana] - required-mana)
                (swap! players assoc-in [id :last-time :skill skill] (now))
                (tea/after! (-> common.skills/skills (get skill) :effect-duration (/ 1000))
                            (bound-fn []
                              (when (get @players id)
                                (send! id :hide-finished true))))
                {:skill skill})))))
