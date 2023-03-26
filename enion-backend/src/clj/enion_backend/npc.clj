(ns enion-backend.npc
  (:require
    [amalloy.ring-buffer :refer :all]))

(defrecord Slot
  [id positions])

(defrecord State
  [name enter update exit])

(defrecord NPC
  [id name state health pos cooldown damage-buffer attack-buffer slot])

(declare change-fsm-state)

(defn- alive? [npc]
  true)

(defn- should-attack-the-player? [npc]
  ;; to be implemented
  true)

(defn- player-close-for-attack? [npc]
  ;; to be implemented
  false)

(defn- need-to-change-pos? [npc]
  ;; to be implemented
  false)

(defn- cooldown-finished? [npc]
  ;; to be implemented
  true)

(def states
  {:idle (->State :idle
                  (fn [npc] (println "Entering IDLE state") npc)
                  (fn [npc]
                    (println "Updating IDLE state")
                    (if (alive? npc)
                      (if (should-attack-the-player? npc)
                        (if (player-close-for-attack? npc)
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
                        (if (should-attack-the-player? npc)
                          (if (player-close-for-attack? npc)
                            (if (cooldown-finished? npc)
                              (do
                                ;; attack
                                npc)
                              npc)
                            (change-fsm-state npc :chase))
                          (change-fsm-state npc :idle))
                        (change-fsm-state npc :die)))
                    (fn [npc] (println "Exiting ATTACK state") npc))
   :chase (->State :chase
                   (fn [npc] (println "Entering CHASE state") npc)
                   (fn [npc] (println "Updating CHASE state")
                     (if (alive? npc)
                       (if (should-attack-the-player? npc)
                         (if (player-close-for-attack? npc)
                           (change-fsm-state npc :attack)
                           (do
                             ;; chase
                             npc))
                         (change-fsm-state npc :idle))
                       (change-fsm-state npc :die)))
                   (fn [npc] (println "Exiting CHASE state") npc))
   :change-pos (->State :change-pos
                        (fn [npc] (println "Entering CHANGE_POS state") npc)
                        (fn [npc]
                          (println "Updating CHANGE_POS state")
                          (if (alive? npc)
                            (if (should-attack-the-player? npc)
                              (if (player-close-for-attack? npc)
                                (change-fsm-state npc :attack)
                                (change-fsm-state npc :chase))
                              (if (need-to-change-pos? npc)
                                (do
                                  ;; change pos
                                  npc)
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

(defn change-fsm-state [npc new-state-name]
  (let [current-state (:state npc)
        new-state (states new-state-name)]
    ((:exit current-state) npc)
    ((:enter new-state) npc)
    (assoc npc :state new-state)))

(defn update-npc [npc]
  (let [current-state (:state npc)]
    (assoc npc :state (:state ((:update current-state) npc)))))

(defn create-npc [{:keys [id name pos health cooldown]}]
  (->NPC id name (states :idle) health pos cooldown (ring-buffer 10) (ring-buffer 10) nil))

(defonce current-npc (atom (create-npc {:id 1 :name "test" :pos [0 0] :health 100 :cooldown 100})))
(defonce player (atom {:id 1 :pos [0 0] :health 100}))

(defn make-player-attack []
  (if (> (:health @current-npc) 0)
    (let [damage (rand-int 10)]
      (println "NPC took damage: " damage)
      (swap! current-npc update :damage-buffer #(conj % {:attacker-id (:id @player)
                                                         :time (System/currentTimeMillis)
                                                         :damage damage})))
    (println "NPC is dead")))

(defn update-npc-pos [npc pos])

(comment
  (make-player-attack)

  (let [npc (create-npc)]
    (-> npc
      (update-npc)
      (update-npc)
      (update-npc)))
  )
