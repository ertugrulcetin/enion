(ns enion-backend.npc
  (:require
    [amalloy.ring-buffer :refer :all]
    [enion-backend.vector2 :as v2]))

(def chase-range-threshold 7)
(def attack-range-threshold 1.5)
(def damage-buffer-size 20)

(defrecord Slot
  [id positions])

(defrecord State
  [name enter update exit])

(defrecord NPC
  [id name state health init-pos pos cooldown damage-buffer slot last-time-attacked last-time-changed-pos])

(declare change-fsm-state states)

(def players
  (atom {1 {:id 1
            :pos [6 0]
            :health 100}}))

(defn create-npc [{:keys [id name init-pos pos health damage-buffer-size cooldown]}]
  (->NPC id
         name
         (states :idle)
         health
         init-pos
         pos
         cooldown
         (ring-buffer damage-buffer-size)
         nil
         nil
         (System/currentTimeMillis)))

(def npcs
  (atom {1 (create-npc {:id 1
                        :name "test"
                        :init-pos [0 0]
                        :pos [0 0]
                        :damage-buffer-size 20
                        :health 100
                        :cooldown 100})}))

(defn- alive? [e]
  (> (:health e) 0))

(defn- get-player-id-to-attack [npc]
  (some-> npc :damage-buffer last :attacker-id))

(defn- player-close-for-attack? [npc player-id]
  (let [player (get @players player-id)]
    (<= (v2/dist (:pos player) (:pos npc)) attack-range-threshold)))

(defn- need-to-change-pos? [npc]
  (or (> (v2/dist (:init-pos npc) (:pos npc)) chase-range-threshold)
      (> (- (System/currentTimeMillis) (:last-time-changed-pos npc)) 5000)
      (and (:target-pos npc) (>= (v2/dist (:pos npc) (:target-pos npc)) 0.2))))

(defn- change-pos [npc]
  (if-let [target (:target-pos npc)]
    (let [pos (:pos npc)
          new-pos (-> target (v2/sub pos) v2/normalize (v2/scale 0.1) (v2/add pos))]
      (if (<= (v2/dist target new-pos) 0.2)
        (assoc npc :pos new-pos :target-pos nil)
        (assoc npc :pos new-pos)))
    (assoc npc :target-pos [0 0]
           :last-time-changed-pos (System/currentTimeMillis))))

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
                           (filter #(alive? (get players (:attacker-id %))))
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
      (swap! npcs update-in [npc-id :damage-buffer] #(conj % {:attacker-id 1
                                                              :damage damage})))
    (println "NPC is dead")))

(defn- chase-player [npc player-id]
  (let [target (get-in @players [player-id :pos])
        pos (:pos npc)
        dir (-> target (v2/sub pos) v2/normalize (v2/scale 0.1))
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

  (make-player-attack! 1)
  (swap! players assoc-in [1 :health] 100)
  (swap! players assoc-in [1 :pos] [15 2])

  (-> (get @npcs 1)
    (update-npc! @players))

  (v2/dist [1.5485071250072668 0.19402850002906638] [2 2])

  (reduce (fn [acc _]
            (add-me acc)) [0 0] (range 34))

  )
