(ns enion-backend.npc.core
  (:require
    [amalloy.ring-buffer :refer :all]
    [clojure.set :as set]
    [clojure.tools.logging :as log]
    [common.enion.npc :as common.npc]
    [common.enion.skills :as common.skills]
    [enion-backend.async :refer [dispatch-in]]
    [enion-backend.npc.slot :as slots]
    [enion-backend.npc.type :as npc.type]
    [enion-backend.utils :as utils]
    [enion-backend.vector2 :as v2]))

;; TODO NPC should be affected by priest's poison, warrior damage booster etc.
;; TODO change player to attack after some threshold e.g. 2 secs, otherwise NPC will be running
;; between player to player, it has to focus a player for a while

(declare states)

(defonce npcs (atom {}))

(defn calculate-exp [level base-exp]
  (max
    ;; min exp
    (int (/ base-exp 10))
    (Math/round (* base-exp (Math/pow (Math/E) (- (* level
                                                     ;; decay-rate
                                                     0.1)))))))

(comment
  (into
    (sorted-map)
    (map
      (fn [level]
        [level (map
                 (fn [[type params]]
                   (let [exp (calculate-exp {:player-level level
                                             :base-exp (:exp params)
                                             :min-exp (/ (:exp params) 10)})
                         required-exp (get common.skills/level->exp-table level)
                         how-many-npcs-to-kill (Math/round (double (/ required-exp exp)))
                         how-many-npcs-to-kill (if (= 0 how-many-npcs-to-kill)
                                                 1
                                                 how-many-npcs-to-kill)
                         attack-power (get common.skills/level->attack-power-table level)]
                     {:type type
                      :how-many-npcs-to-kill how-many-npcs-to-kill
                      :attack-power attack-power
                      :exp exp}))
                 npc.type/npc-params)])
      (range 1 30))))

(defn create-npc [{:keys [init-pos slot-id type taken-slot-pos-id]}]
  (let [attrs (npc.type/npc-params type)
        change-pos-interval (:change-pos-interval attrs)]
    (merge
      attrs
      {:id (swap! utils/id-generator inc)
       :change-pos-interval (utils/rand-between change-pos-interval (+ change-pos-interval 5000))
       :pos init-pos
       :damage-buffer (ring-buffer (:damage-buffer-size attrs))
       :init-pos init-pos
       :slot-id slot-id
       :state (states :idle)
       :taken-slot-pos-id taken-slot-pos-id
       :type type
       :last-time-changed-pos (System/currentTimeMillis)})))

(defn- get-state-by-id [npc-id]
  (get-in @npcs [npc-id :state :name]))

(defn- find-available-slot-pos-id [npcs slot-id]
  (let [slot-pos-ids (set (keys (get slots/slot-positions slot-id)))
        taken-slot-pos-ids (->> (vals @npcs)
                                (filter #(= slot-id (:slot-id %)))
                                (map :taken-slot-pos-id)
                                set)]
    (-> (set/difference slot-pos-ids taken-slot-pos-ids) shuffle first)))

(defn- add-npc [npcs {:keys [type slot-id]}]
  (let [available-slot-pos-id (find-available-slot-pos-id npcs slot-id)
        init-pos (get-in slots/slot-positions [slot-id available-slot-pos-id])
        npc (create-npc {:type type
                         :slot-id slot-id
                         :init-pos init-pos
                         :taken-slot-pos-id available-slot-pos-id})]
    (swap! npcs assoc (:id npc) npc)))

(defn- alive? [e]
  (> (:health e) 0))

(defn- hide? [attacker-id players]
  (get-in players [attacker-id :effects :hide :result]))

(defn- get-player-id-to-attack [npc world players]
  (let [locker-player-id (:locked-player-id npc)]
    (or (and (not (hide? locker-player-id players))
             locker-player-id)
        (some->> npc
                 :damage-buffer
                 (filter (fn [{:keys [attacker-id]}]
                           (let [player (get world attacker-id)
                                 player-pos [(:px player) (:pz player)]]
                             (and player
                                  (<= (v2/dist (:init-pos npc) player-pos) (:chase-range-threshold npc))
                                  (not (hide? attacker-id players))))))
                 last
                 :attacker-id)
        (and (:attack-when-close-range-threshold npc)
             (->> (keys players)
                  (filter (fn [player-id]
                            (let [player (get world player-id)
                                  player-pos [(:px player) (:pz player)]]
                              (and player
                                   (alive? player)
                                   (<= (v2/dist (:pos npc) player-pos) (:attack-when-close-range-threshold npc))
                                   (not (hide? player-id players))))))
                  first)))))

(defn- player-close-for-attack? [npc player-id world]
  (let [player (get world player-id)
        player-pos [(:px player) (:pz player)]]
    (and player (<= (v2/dist player-pos (:pos npc)) (:attack-range-threshold npc)))))

(defn- need-to-change-pos? [npc]
  (or (> (v2/dist (:init-pos npc) (:pos npc)) (:chase-range-threshold npc))
      (> (- (System/currentTimeMillis) (:last-time-changed-pos npc)) (:change-pos-interval npc))
      (and (:target-pos npc) (>= (v2/dist (:pos npc) (:target-pos npc)) (:target-pos-gap-threshold npc)))))

(def temp-to-add [0.1 0.1])

(defn- change-pos [npc]
  (if-let [target (:target-pos npc)]
    (let [pos (:pos npc)
          target (if (= target pos) (v2/add target temp-to-add) target)
          change-pos-speed (if (and (:last-time-slow-down npc)
                                    (< (- (System/currentTimeMillis) (:last-time-slow-down npc)) 5000))
                             (/ (:change-pos-speed npc) 3)
                             (:change-pos-speed npc))
          new-pos (-> target (v2/sub pos) v2/normalize (v2/scale change-pos-speed) (v2/add pos))]
      (if (<= (v2/dist target new-pos) (:target-pos-gap-threshold npc))
        (assoc npc :pos new-pos :target-pos nil)
        (assoc npc :pos new-pos)))
    (let [slot-id (:slot-id npc)
          available-slot-pos-id (find-available-slot-pos-id npcs slot-id)
          target-pos (get-in slots/slot-positions [slot-id available-slot-pos-id])]
      (assoc npc :target-pos target-pos
             :init-pos target-pos
             :taken-slot-pos-id available-slot-pos-id
             :last-time-changed-pos (System/currentTimeMillis)))))

(defn- cooldown-finished? [npc]
  (let [now (System/currentTimeMillis)]
    (>= (- now (or (:last-time-attacked npc) 0)) (:cooldown npc))))

(defn change-fsm-state
  ([npc new-state]
   (change-fsm-state npc new-state nil nil nil))
  ([npc new-state player-id world players]
   (let [current-state (:state npc)
         new-state (states new-state)
         npc ((:exit current-state) npc)
         npc ((:enter new-state) (assoc npc :target-player-id player-id) world players)]
     (assoc npc :state new-state))))

(defn- unlock-target-player? [{:keys [init-pos
                                      locked-player-id
                                      last-locked-to-player
                                      chase-range-threshold
                                      target-locked-threshold]} world]
  (let [now (System/currentTimeMillis)
        player (get world locked-player-id)
        player-pos [(:px player) (:pz player)]]
    (or (and last-locked-to-player (>= (- now last-locked-to-player) target-locked-threshold))
        (and locked-player-id (not (some->> locked-player-id (get world) alive?)))
        (and player (> (v2/dist init-pos player-pos) chase-range-threshold)))))

(defn update-npc! [npc world players]
  (try
    (let [current-state (:state npc)
          npc (if (unlock-target-player? npc world)
                (dissoc npc :locked-player-id :last-locked-to-player)
                npc)
          players-died (remove #(some->> (:attacker-id %) (get world) alive?) (:damage-buffer npc))
          npc (if (seq players-died)
                (assoc npc :damage-buffer (->> (:damage-buffer npc)
                                               (filter #(some->> (:attacker-id %) (get world) alive?))
                                               (into (ring-buffer (:damage-buffer-size npc)))))
                npc)
          npc ((:update current-state) npc world players)]
      (swap! npcs assoc (:id npc) npc)
      npc)
    (catch Exception e
      (println e))))

(defn update-all-npcs! [world players]
  (doseq [[_ npc] @npcs]
    (update-npc! npc world players))
  (map
    (fn [[_ npc]]
      (cond-> {:id (:id npc)
               :health (:health npc)
               :state (-> npc :state :name)
               :px (-> npc :pos first)
               :pz (-> npc :pos second)}
        (:target-player-id npc) (assoc :target-player-id (:target-player-id npc))))
    @npcs))

(defn attack-to-player [npc player-id world players]
  (let [damage ((:damage-fn npc))
        npc (assoc npc :last-time-attacked (System/currentTimeMillis))]
    (dispatch-in :skill {:id player-id
                         :data {:skill "npcDamage"
                                :damage damage
                                :npc-id (:id npc)}
                         :world world
                         :players players})
    npc))

(defn make-player-attack! [{:keys [skill player npc slow-down? break-defense? attack-power]}]
  (try
    (let [attacker-id (:id player)
          attacker-party-id (:party-id player)
          now (System/currentTimeMillis)
          damage-fn (some-> common.skills/skills (get skill) :damage-fn)
          damage (if damage-fn
                   (if attack-power
                     (damage-fn false false attack-power)
                     (damage-fn false false))
                   0)
          npc-damage-ratio (+ 1 (double (/ (utils/rand-between 20 27) 100)))
          damage (int (* damage npc-damage-ratio))
          last-break-defense (:last-time-break-defense npc)
          damage (if (and last-break-defense
                          (< (- now last-break-defense) (-> common.skills/skills (get "breakDefense") :effect-duration)))
                   (int (* damage 1.3))
                   damage)
          health-after-damage (- (:health npc) damage)
          health-after-damage (Math/max ^long health-after-damage 0)
          npc-id (:id npc)]
      (swap! npcs (fn [npcs]
                    (let [npcs (-> npcs
                                   (update-in [npc-id :damage-buffer] #(conj % {:attacker-id attacker-id
                                                                                :attacker-party-id attacker-party-id
                                                                                :damage damage
                                                                                :time (System/currentTimeMillis)}))
                                   (assoc-in [npc-id :health] health-after-damage))]
                      (cond-> npcs
                        slow-down? (assoc-in [npc-id :last-time-slow-down] now)
                        break-defense? (assoc-in [npc-id :last-time-break-defense] now)))))
      damage)
    (catch Exception e
      (println "Error in make-player-attack!")
      (log/error e))))

(defn find-top-damager [damage-buffer]
  (let [grouped-by-party-id (group-by :attacker-party-id damage-buffer)
        party-grouped-damage (dissoc grouped-by-party-id nil)
        party-total-damage (map
                             (fn [[party-id damages]]
                               [party-id (reduce + (map :damage damages))])
                             party-grouped-damage)
        most-damaged-party (some->> (seq party-total-damage) (cons second) (apply max-key))
        [most-damaged-party-id most-damaged-party-damage] most-damaged-party
        grouped-by-players-without-party (->> (get grouped-by-party-id nil) (group-by :attacker-id))
        players-total-damage (map
                               (fn [[player-id damages]]
                                 [player-id (reduce + (map :damage damages))])
                               grouped-by-players-without-party)
        most-damaged-player (some->> (seq players-total-damage) (cons second) (apply max-key))
        [most-damaged-player-id most-damaged-player-damage] most-damaged-player]
    (if (> (or most-damaged-party-damage 0) (or most-damaged-player-damage 0))
      {:party-id most-damaged-party-id}
      {:player-id most-damaged-player-id})))

(defn- chase-player [npc player-id world]
  (if-let [player (get world player-id)]
    (let [target [(:px player) (:pz player)]
          pos (:pos npc)
          target (if (= target pos) (v2/add target temp-to-add) target)
          chase-speed (if (and (:last-time-slow-down npc)
                               (< (- (System/currentTimeMillis) (:last-time-slow-down npc)) 5000))
                        (/ (:chase-speed npc) 3)
                        (:chase-speed npc))
          dir (-> target (v2/sub pos) v2/normalize (v2/scale chase-speed))
          new-pos (v2/add pos dir)]
      (assoc npc :pos new-pos))
    npc))

(defn- lock-npc-to-player [npc]
  (let [player-id (:target-player-id npc)
        now (System/currentTimeMillis)]
    (if (or (nil? (:locked-player-id npc))
            (>= (- now (:last-locked-to-player npc)) (:target-locked-threshold npc)))
      (assoc npc :locked-player-id player-id
             :last-locked-to-player now)
      npc)))

(def states
  {:idle {:name :idle
          :enter (fn [npc world players]
                   ;; (println "Entering IDLE state")
                   npc)
          :update (fn [npc world players]
                    (if (alive? npc)
                      (if-let [player-id (get-player-id-to-attack npc world players)]
                        (if (player-close-for-attack? npc player-id world)
                          (change-fsm-state npc :attack player-id world players)
                          (change-fsm-state npc :chase player-id world players))
                        (if (need-to-change-pos? npc)
                          (change-fsm-state npc :change-pos)
                          npc))
                      (change-fsm-state npc :die)))
          :exit (fn [npc]
                  ;; (println "Exiting IDLE state")
                  npc)}
   :attack {:name :attack
            :enter (fn [npc world players]
                     ;; (println "Entering ATTACK state")
                     (attack-to-player npc (:target-player-id npc) world players)
                     (-> npc
                         (assoc :last-time-attacked (System/currentTimeMillis))
                         (lock-npc-to-player)))
            :update (fn [npc world players]
                      (let [last-time-attacked (:last-time-attacked npc)
                            now (System/currentTimeMillis)]
                        (if (alive? npc)
                          (if (and last-time-attacked (<= (- now last-time-attacked) 1000))
                            npc
                            (if-let [player-id (get-player-id-to-attack npc world players)]
                              (if (player-close-for-attack? npc player-id world)
                                (if (cooldown-finished? npc)
                                  (attack-to-player npc player-id world players)
                                  npc)
                                (change-fsm-state npc :chase player-id world players))
                              (change-fsm-state npc :idle)))
                          (change-fsm-state npc :die))))
            :exit (fn [npc]
                    ;; (println "Exiting ATTACK state")
                    npc)}
   :chase {:name :chase
           :enter (fn [npc world players]
                    ;; (println "Entering CHASE state")
                    (lock-npc-to-player npc))
           :update (fn [npc world players]
                     (if (alive? npc)
                       (if-let [player-id (get-player-id-to-attack npc world players)]
                         (if (player-close-for-attack? npc player-id world)
                           (change-fsm-state npc :attack player-id world players)
                           (chase-player npc player-id world))
                         (change-fsm-state npc :idle))
                       (change-fsm-state npc :die)))
           :exit (fn [npc]
                   ;; (println "Exiting CHASE state")
                   npc)}
   :change-pos {:name :change-pos
                :enter (fn [npc world players]
                         ;; (println "Entering CHANGE_POS state")
                         npc)
                :update (fn [npc world players]
                          (if (alive? npc)
                            (if-let [player-id (get-player-id-to-attack npc world players)]
                              (if (player-close-for-attack? npc player-id world)
                                (change-fsm-state npc :attack player-id world players)
                                (change-fsm-state npc :chase player-id world players))
                              (if (need-to-change-pos? npc)
                                (change-pos npc)
                                (change-fsm-state npc :idle)))
                            (change-fsm-state npc :die)))
                :exit (fn [npc]
                        ;; (println "Exiting CHANGE_POS state")
                        npc)}
   ;; TODO only give drop if player is in range, this is for party members
   :die {:name :die
         :enter (fn [npc world players]
                  ;; (println "Entering DIE state")
                  (dispatch-in :drop {:data {:top-damager (find-top-damager (:damage-buffer npc))
                                             :npc npc}})
                  (-> npc
                      (assoc :last-time-died (System/currentTimeMillis))
                      (assoc :damage-buffer (ring-buffer (:damage-buffer-size npc)))))
         :update (fn [npc _ _]
                   (if (>= (- (System/currentTimeMillis) (:last-time-died npc)) (:re-spawn-interval npc))
                     (let [slot-id (:slot-id npc)
                           available-slot-pos-id (find-available-slot-pos-id npcs slot-id)
                           pos (get-in slots/slot-positions [slot-id available-slot-pos-id])]
                       (-> npc
                           (assoc :health (get-in common.npc/npcs [(:type npc) :health])
                                  :pos pos)
                           (change-fsm-state :idle)))
                     npc))
         :exit (fn [npc]
                 ;; (println "Exiting DIE state")
                 npc)}})

(defn init-npcs []
  (doseq [{:keys [type slot-id count]} slots/slots]
    (dotimes [_ count]
      (add-npc npcs {:type type
                     :slot-id slot-id}))))

(defn clear-npcs []
  (reset! npcs {}))

(defn npc-types->ids []
  (reduce-kv
    (fn [acc k v]
      (assoc acc k (map :id v)))
    {}
    (group-by :type (vals @npcs))))

(comment
  @npcs

  (do
    (clear-npcs)
    (init-npcs))

  ;;get height if a given x and z points in a mesh



  (v2/dist [1.5485071250072668 0.19402850002906638] [2 2])

  (reduce (fn [acc _]
            (add-me acc)) [0 0] (range 34))

  )
