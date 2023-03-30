(ns enion-backend.npc
  (:require
    [amalloy.ring-buffer :refer :all]
    [clojure.set :as set]
    [enion-backend.vector2 :as v2]))

;; TODO NPC should be affected by priest's poison, warrior damage booster etc.
;; TODO change player to attack after some threshold e.g. 2 secs, otherwise NPC will be running
;; between player to player, it has to focus a player for a while

(def chase-range-threshold 7)
(def attack-range-threshold 1.5)
(def damage-buffer-size 20)
(def last-time-changed-pos-threshold 5000)
(def target-pos-gap-threshold 0.2)
(def npc-speed 0.1)

(defonce slots (atom {0 {0 [1 1]
                         1 [2 2]
                         2 [3 3]
                         3 [3.5 3.5]
                         4 [4 4]}}))

(defrecord State
  [name enter update exit])

(defrecord NPC
  [id
   name
   state
   health
   init-pos
   pos
   cooldown
   damage-buffer
   slot-id
   taken-slot-pos-id
   last-time-attacked
   last-time-changed-pos])

(declare change-fsm-state states)

(def players
  (atom {1 {:id 1
            :pos [6 0]
            :health 100}}))

(defn create-npc [{:keys [id name init-pos pos health damage-buffer-size cooldown slot-id taken-slot-pos-id]}]
  (->NPC
    id
    name
    (states :idle)
    health
    init-pos
    pos
    cooldown
    (ring-buffer damage-buffer-size)
    slot-id
    taken-slot-pos-id
    nil
    (System/currentTimeMillis)))

(defonce npcs (atom {}))

(defn- clear-npcs []
  (reset! npcs {}))

(defn- find-available-slot-pos-id [npcs slot-id]
  (let [slot-pos-ids (set (keys (get @slots slot-id)))
        taken-slot-pos-ids (set (map :taken-slot-pos-id (vals @npcs)))]
    (-> (set/difference slot-pos-ids taken-slot-pos-ids) shuffle first)))

(defn- add-npc [npcs {:keys [id name health damage-buffer-size cooldown slot-id]}]
  (let [available-slot-pos-id (find-available-slot-pos-id npcs slot-id)
        init-pos (get-in @slots [slot-id available-slot-pos-id])]
    (swap! npcs assoc id (create-npc {:id id
                                      :name name
                                      :init-pos init-pos
                                      :pos init-pos
                                      :slot-id slot-id
                                      :taken-slot-pos-id available-slot-pos-id
                                      :damage-buffer-size damage-buffer-size
                                      :health health
                                      :cooldown cooldown}))))

(defn- alive? [e]
  (> (:health e) 0))

(defn- get-player-id-to-attack [npc]
  (some-> npc :damage-buffer last :attacker-id))

(defn- player-close-for-attack? [npc player-id]
  (let [player (get @players player-id)]
    (<= (v2/dist (:pos player) (:pos npc)) attack-range-threshold)))

(defn- need-to-change-pos? [npc]
  (or (> (v2/dist (:init-pos npc) (:pos npc)) chase-range-threshold)
      (> (- (System/currentTimeMillis) (:last-time-changed-pos npc)) last-time-changed-pos-threshold)
      (and (:target-pos npc) (>= (v2/dist (:pos npc) (:target-pos npc)) target-pos-gap-threshold))))

(defn- change-pos [npc]
  (if-let [target (:target-pos npc)]
    (let [pos (:pos npc)
          new-pos (-> target (v2/sub pos) v2/normalize (v2/scale npc-speed) (v2/add pos))]
      (if (<= (v2/dist target new-pos) target-pos-gap-threshold)
        (assoc npc :pos new-pos :target-pos nil)
        (assoc npc :pos new-pos)))
    (let [slot-id (:slot-id npc)
          available-slot-pos-id (find-available-slot-pos-id npcs slot-id)
          target-pos (get-in @slots [slot-id available-slot-pos-id])]
      (assoc npc :target-pos target-pos
             :init-pos target-pos
             :taken-slot-pos-id available-slot-pos-id
             :last-time-changed-pos (System/currentTimeMillis)))))

(defn- cooldown-finished? [npc]
  (let [now (System/currentTimeMillis)]
    (>= (- now (or (:last-time-attacked npc) 0)) (:cooldown npc))))

(defn change-fsm-state [npc new-state-name]
  (let [current-state (:state npc)
        new-state (states new-state-name)]
    ((:exit current-state) npc)
    ((:enter new-state) npc)
    (assoc npc :state new-state)))

(defn update-npc! [npc players]
  (let [damage-buffer (->> (:damage-buffer npc)
                           (filter #(some->> (:attacker-id %) (get players) alive?))
                           (filter #(<= (v2/dist (:init-pos npc) (:pos (get players (:attacker-id %)))) chase-range-threshold)))
        current-state (:state npc)
        npc (assoc npc :damage-buffer (into (ring-buffer damage-buffer-size) damage-buffer))
        npc ((:update current-state) npc)]
    (swap! npcs assoc (:id npc) npc)
    npc))

(defn attack-to-player [npc player-id]
  (let [damage (rand-int 10)
        now (System/currentTimeMillis)
        npc (assoc npc :last-time-attacked now)]
    (swap! players update player-id #(assoc % :health (- (:health %) damage)))
    (println "NPC attacked player: " damage)
    npc))

(defn make-player-attack! [npc-id]
  (if (> (get-in @npcs [npc-id :health]) 0)
    (let [damage (rand-int 10)]
      (println "NPC took damage: " damage)
      (swap! npcs (fn [npcs]
                    (let [health-after-damage (- (get-in npcs [npc-id :health]) damage)
                          health-after-damage (Math/max ^long health-after-damage 0)]
                      (-> npcs
                          (update-in [npc-id :damage-buffer] #(conj % {:attacker-id 1 :damage damage}))
                          (assoc-in [npc-id :health] health-after-damage))))))
    (println "NPC is dead")))

(defn- chase-player [npc player-id]
  (let [target (get-in @players [player-id :pos])
        pos (:pos npc)
        dir (-> target (v2/sub pos) v2/normalize (v2/scale npc-speed))
        new-pos (v2/add pos dir)]
    (assoc npc :pos new-pos)))

(def states
  {:idle (->State :idle
                  (fn [npc] (println "Entering IDLE state") npc)
                  (fn [npc]
                    (println "Updating IDLE state")
                    (if (alive? npc)
                      (if-let [player-id (get-player-id-to-attack npc)]
                        (if (player-close-for-attack? npc player-id)
                          (change-fsm-state npc :attack)
                          (change-fsm-state npc :chase))
                        (if (need-to-change-pos? npc)
                          (change-fsm-state npc :change-pos)
                          npc))
                      (change-fsm-state npc :die)))
                  (fn [npc] (println "Exiting IDLE state") npc))
   :attack (->State :attack
                    (fn [npc] (println "Entering ATTACK state") npc)
                    (fn [npc]
                      (println "Updating ATTACK state")
                      (if (alive? npc)
                        (if-let [player-id (get-player-id-to-attack npc)]
                          (if (player-close-for-attack? npc player-id)
                            (if (cooldown-finished? npc)
                              (attack-to-player npc player-id)
                              npc)
                            (change-fsm-state npc :chase))
                          (change-fsm-state npc :idle))
                        (change-fsm-state npc :die)))
                    (fn [npc] (println "Exiting ATTACK state") npc))
   :chase (->State :chase
                   (fn [npc] (println "Entering CHASE state") npc)
                   (fn [npc] (println "Updating CHASE state")
                     (if (alive? npc)
                       (if-let [player-id (get-player-id-to-attack npc)]
                         (if (player-close-for-attack? npc player-id)
                           (change-fsm-state npc :attack)
                           (chase-player npc player-id))
                         (change-fsm-state npc :idle))
                       (change-fsm-state npc :die)))
                   (fn [npc] (println "Exiting CHASE state") npc))
   :change-pos (->State :change-pos
                        (fn [npc] (println "Entering CHANGE_POS state") npc)
                        (fn [npc]
                          (println "Updating CHANGE_POS state")
                          (if (alive? npc)
                            (if-let [player-id (get-player-id-to-attack npc)]
                              (if (player-close-for-attack? npc player-id)
                                (change-fsm-state npc :attack)
                                (change-fsm-state npc :chase))
                              (if (need-to-change-pos? npc)
                                (change-pos npc)
                                (change-fsm-state npc :idle)))
                            (change-fsm-state npc :die)))
                        (fn [npc] (println "Exiting CHANGE_POS state") npc))
   :die (->State :die
                 (fn [npc] (println "Entering DIE state") npc)
                 (fn [npc]
                   (println "Updating DIE state")
                   (if (alive? npc)
                     (change-fsm-state npc :idle)
                     npc))
                 (fn [npc] (println "Exiting DIE state") npc))})

(defn add-me [pos]
  (-> [2 2]
      (v2/sub pos)
      v2/normalize
      (v2/scale 0.1)
      (v2/add pos)))

(comment
  @npcs

  (clear-npcs)

  (doseq [npc [{:id 0
                :name "Knight Slayer"
                :slot-id 0
                :damage-buffer-size 20
                :health 100
                :cooldown 1000}
               {:id 1
                :name "Knight Slayer 2"
                :slot-id 0
                :damage-buffer-size 20
                :health 100
                :cooldown 1000}
               {:id 2
                :name "Knight Slayer 3"
                :slot-id 0
                :damage-buffer-size 20
                :health 100
                :cooldown 1000}]]
    (add-npc npcs npc))
  ;;get height if a given x and z points in a mesh
  (make-player-attack! 1)
  (swap! players assoc-in [1 :health] 100)
  (swap! players assoc-in [1 :pos] [15 2])

  (do
    (dotimes [_ 5]
      (doseq [[_ npc] @npcs]
        (update-npc! npc @players)))
    @npcs)

  (-> (get @npcs 1)
    (update-npc! @players))

  (v2/dist [1.5485071250072668 0.19402850002906638] [2 2])

  (reduce (fn [acc _]
            (add-me acc)) [0 0] (range 34))

  )
