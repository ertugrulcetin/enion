(ns enion-backend.routes.home
  (:require
    [aleph.http :as http]
    [clojure.java.io :as io]
    [clojure.string :as str]
    [clojure.tools.logging :as log]
    [common.enion.skills :as common.skills]
    [enion-backend.async :refer [dispatch reg-pro]]
    [enion-backend.layout :as layout]
    [enion-backend.middleware :as middleware]
    [enion-backend.teatime :as tea]
    [enion-backend.utils :as utils]
    [manifold.deferred :as d]
    [manifold.stream :as s]
    [mount.core :as mount :refer [defstate]]
    [msgpack.clojure-extensions]
    [msgpack.core :as msg]
    [ring.util.http-response :as response]
    [ring.util.response])
  (:import
    (java.time
      Instant)
    (java.util.concurrent
      ExecutorService
      Executors
      TimeUnit)))

(def battle-points-by-party-size
  {1 72
   2 36
   3 24
   4 20
   5 15})

(def max-number-of-players 50)
(def max-number-of-same-race-players (/ max-number-of-players 2))

(def send-snapshot-count 20)
(def world-tick-rate (/ 1000 send-snapshot-count))

(defonce id-generator (atom 0))
(defonce party-id-generator (atom 0))

(def max-number-of-party-members 5)

(defonce players (atom {}))
(defonce world (atom {}))

(defonce effects-stream (s/stream))
(defonce killings-stream (s/stream))

(defn- add-effect [effect data]
  (s/put! effects-stream {:effect effect
                          :data data}))

(defn- add-killing [killer-id killed-id]
  (s/put! killings-stream {:killer-id killer-id
                           :killed-id killed-id}))

(defn- take-while-stream [pred stream]
  (loop [result []]
    (let [value @(s/try-take! stream 0)]
      (if (pred value)
        (recur (conj result value))
        result))))

(defn- create-effects->player-ids-mapping [effects]
  (->> effects
       (group-by :effect)
       (reduce-kv
         (fn [acc k v]
           (assoc acc k (vec (distinct (map :data v))))) {})))

(defn home-page
  [request]
  (layout/render request "index.html"))

(defn- send!
  [player-id pro-id result]
  ;; TODO check if socket closed or not!
  (when result
    (when-let [socket (get-in @players [player-id :socket])]
      (s/put! socket (msg/pack (hash-map pro-id result))))))

(defn rand-between [min max]
  (+ (Math/floor (* (Math/random) (+ (- max min) 1))) min))

(defn- send-world-snapshots* []
  (let [effects (->> effects-stream
                     (take-while-stream (comp not nil?))
                     create-effects->player-ids-mapping)
        kills (take-while-stream (comp not nil?) killings-stream)
        w @world
        w (if (empty? effects) w (assoc w :effects effects))
        w (if (empty? kills) w (assoc w :kills kills))]
    (doseq [player-id (keys w)]
      (send! player-id :world-snapshot w))))

(defn- send-world-snapshots []
  (let [ec (Executors/newSingleThreadScheduledExecutor)]
    (doto ec
      (.scheduleAtFixedRate
        (fn []
          (send-world-snapshots*))
        0
        world-tick-rate
        TimeUnit/MILLISECONDS))))

(defn- shutdown [^ExecutorService ec]
  (.shutdown ec)
  (try
    (when-not (.awaitTermination ec 2 TimeUnit/SECONDS)
      (.shutdownNow ec))
    (catch InterruptedException _
      ;; TODO may no need interrupt fn
      (.. Thread currentThread interrupt)
      (.shutdownNow ec))))

(defstate ^{:on-reload :noop} snapshot-sender
  :start (send-world-snapshots)
  :stop (shutdown snapshot-sender))

(defstate ^{:on-reload :noop} teatime-pool
  :start (tea/start!)
  :stop (tea/stop!))

(defn random-pos-for-orc
  []
  [(+ 38 (rand 1)) 0.57 (- (+ 39 (rand 4)))])

(defn random-pos-for-human
  []
  [(- (+ 38 (rand 5.5))) 0.57 (+ 39 (rand 1.5))])

(reg-pro
  :set-state
  (fn [{:keys [id data]}]
    ;; TODO gets the data right away, need to select keys...to prevent hacks
    (swap! world update id merge data)
    nil))

(defn- find-player-ids-by-race [players race]
  (->> players
       (filter
         (fn [[_ v]]
           (= race (:race v))))
       (map first)))

(defn- find-player-ids-by-party-id [players party-id]
  (->> players
       (filter
         (fn [[_ v]]
           (= party-id (:party-id v))))
       (map first)))

(defn now []
  (.toEpochMilli (Instant/now)))

(def message-sent-too-often-msg {:error :message-sent-too-often})

(defn- message-sent-too-often? [player]
  (let [last-time (get-in player [:last-time :message-sent])
        now (now)]
    (and last-time (< (- now last-time) 1000))))

(reg-pro
  :send-global-message
  (fn [{:keys [id] {:keys [msg]} :data}]
    (let [players* @players
          player (players* id)
          race (:race player)
          player-ids (find-player-ids-by-race players* race)
          message-sent-too-often? (message-sent-too-often? player)]
      (swap! players assoc-in [id :last-time :message-sent] (now))
      (cond
        message-sent-too-often? message-sent-too-often-msg
        :else
        (when (not (or (str/blank? msg)
                       (> (count msg) 80)))
          (doseq [id player-ids]
            (send! id :global-message {:id id
                                       :msg msg})))))))

(reg-pro
  :send-party-message
  (fn [{:keys [id] {:keys [msg]} :data}]
    (let [players* @players
          player (players* id)
          party-id (:party-id player)
          message-sent-too-often? (message-sent-too-often? player)]
      (swap! players assoc-in [id :last-time :message-sent] (now))
      (cond
        message-sent-too-often? message-sent-too-often-msg
        :else
        (when (not (or (str/blank? msg)
                       (> (count msg) 80)
                       (nil? party-id)))
          (doseq [id (find-player-ids-by-party-id players* party-id)]
            (send! id :party-message {:id id
                                      :msg msg})))))))

(defn- notify-players-for-new-join [id attrs]
  (doseq [other-player-id (filter #(not= id %) (keys @world))]
    (println "Sending new join...")
    (send! other-player-id :player-join attrs)))

(defn- notify-players-for-exit [id]
  (doseq [other-player-id (filter #(not= id %) (keys @world))]
    (println "Sending exit notification...")
    (send! other-player-id :player-exit id)))

(defn- prob? [prob]
  (< (rand) prob))

(def race-set #{"orc" "human"})
(def class-set #{"warrior" "mage" "asas" "priest"})

(defn- get-usernames []
  (->> @players
       vals
       (keep :username)
       (map str/lower-case)
       set))

(reg-pro
  :init
  (fn [{id :id {:keys [username race class]} :data}]
    (cond
      (not (common.skills/username? username)) {:error :invalid-username}
      ((get-usernames) (str/lower-case username)) {:error :username-taken}
      (not (race-set race)) {:error :invalid-race}
      (not (class-set class)) {:error :invalid-class}
      :else (do
              (println "Player joining...")
              (let [pos (if (= "orc" race)
                          (random-pos-for-orc)
                          (random-pos-for-human))
                    health (get-in common.skills/classes [class :health])
                    mana (get-in common.skills/classes [class :mana])
                    attrs {:id id
                           ;; :username (str username "_" id)
                           :username username
                           ;; :race (if (odd? id) "human" "orc")
                           :race race
                           :class class
                           :health health
                           :mana mana
                           :pos pos}]
                (swap! players update id merge attrs)
                (notify-players-for-new-join id attrs)
                attrs)))))

(reg-pro
  :connect-to-world-state
  (fn [{:keys [id]}]
    (let [{:keys [pos health mana]} (get @players id)
          [x y z] pos]
      (swap! world (fn [world]
                     (-> world
                         (assoc-in [id :px] x)
                         (assoc-in [id :py] y)
                         (assoc-in [id :pz] z)
                         (assoc-in [id :health] health)
                         (assoc-in [id :mana] mana)))))
    (println "Connected to world state")
    true))

(reg-pro
  :request-all-players
  (fn [_]
    (map #(select-keys % [:id :username :race :class :health :mana :pos]) (vals @players))))

(def skill-failed {:error :skill-failed})
(def too-far {:error :too-far})
(def not-enough-mana {:error :not-enough-mana})
(def party-request-failed {:error :party-request-failed})

(defn- alive? [state]
  (> (:health state) 0))

(defn- enough-mana? [skill state]
  (>= (:mana state) (-> common.skills/skills (get skill) :required-mana)))

(defn asas? [id]
  (= "asas" (get-in @players [id :class])))

(defn mage? [id]
  (= "mage" (get-in @players [id :class])))

(defn warrior? [id]
  (= "warrior" (get-in @players [id :class])))

(defn priest? [id]
  (= "priest" (get-in @players [id :class])))

(defn has-defense? [id]
  (get-in @players [id :effects :shield-wall :result]))

(defn has-break-defense? [id]
  (get-in @players [id :effects :break-defense :result]))

(defn- cooldown-finished? [skill player]
  (let [cooldown (if (and (= skill "fleetFoot") (asas? (:id player)))
                   (-> common.skills/skills (get skill) :cooldown-asas)
                   (-> common.skills/skills (get skill) :cooldown))
        last-time-skill-used (get-in player [:last-time :skill skill])]
    (or (nil? last-time-skill-used)
        (>= (- (now) last-time-skill-used) cooldown))))

(defn- get-required-mana [skill]
  (-> common.skills/skills (get skill) :required-mana))

(defn use-potion [health-or-mana value max]
  (Math/min ^long (+ health-or-mana value) ^long max))

(defn- distance
  ([x x1 z z1]
   (let [dx (- x x1)
         dz (- z z1)]
     (Math/sqrt (+ (* dx dx) (* dz dz)))))
  ([x x1 y y1 z z1]
   (let [dx (- x x1)
         dy (- y y1)
         dz (- z z1)]
     (Math/sqrt (+ (* dx dx) (* dy dy) (* dz dz))))))

(defmulti apply-skill (fn [{:keys [data]}]
                        (:skill data)))

(defn- close-distance? [player-world-state other-player-world-state threshold]
  (let [x (:px player-world-state)
        y (:py player-world-state)
        z (:pz player-world-state)
        x1 (:px other-player-world-state)
        y1 (:py other-player-world-state)
        z1 (:pz other-player-world-state)]
    (<= (distance x x1 y y1 z z1) threshold)))

(defn- close-for-attack? [player-world-state other-player-world-state]
  (close-distance? player-world-state other-player-world-state (+ common.skills/close-attack-distance-threshold 0.25)))

(defn- close-for-priest-skills? [player-world-state other-player-world-state]
  (close-distance? player-world-state other-player-world-state (+ common.skills/priest-skills-distance-threshold 0.5)))

(defn- close-for-attack-single? [player-world-state other-player-world-state]
  (close-distance? player-world-state other-player-world-state (+ common.skills/attack-single-distance-threshold 0.5)))

(defn- attack-range-in-distance? [world-state x y z]
  (let [{:keys [px py pz]} world-state]
    (<= (distance px x py y pz z) common.skills/attack-range-distance-threshold)))

(defn hidden? [selected-player-id]
  (get-in @players [selected-player-id :effects :hide :result]))

(defn make-asas-appear-if-hidden [selected-player-id]
  (when (hidden? selected-player-id)
    (add-effect :appear selected-player-id)
    (send! selected-player-id :hide-finished true)))

(defn enemy? [player-id selected-player-id]
  (not= (get-in @players [player-id :race])
        (get-in @players [selected-player-id :race])))

(defn ally? [player-id selected-player-id]
  (= (get-in @players [player-id :race])
     (get-in @players [selected-player-id :race])))

(defn- square [n]
  (Math/pow n 2))

(defn inside-circle?
  "Clojure formula of (x - center_x)² + (y - center_y)² < radius²"
  [x z center-x center-z radius]
  (< (+ (square (- x center-x)) (square (- z center-z))) (square radius)))

(defn- process-if-death [player-id enemy-id health-after-damage players*]
  (when (= 0 health-after-damage)
    (add-killing player-id enemy-id)
    (swap! players assoc-in [enemy-id :last-time :died] (now))
    (if-let [party-id (get-in players* [player-id :party-id])]
      (let [member-ids (find-player-ids-by-party-id players* party-id)
            party-size (count member-ids)
            bp (battle-points-by-party-size party-size)]
        (doseq [id member-ids]
          (send! id :earned-bp bp)
          (swap! players update-in [id :bp] (fnil + 0) bp)))
      (let [bp (battle-points-by-party-size 1)]
        (send! player-id :earned-bp bp)
        (swap! players update-in [player-id :bp] (fnil + 0) bp)))))

;; TODO make asas hide false when he gets damage
(defmethod apply-skill "attackOneHand" [{:keys [id ping] {:keys [skill selected-player-id]} :data}]
  (let [players* @players]
    (when-let [player (get players* id)]
      (when (get players* selected-player-id)
        (let [w @world
              player-world-state (get w id)
              other-player-world-state (get w selected-player-id)]
          (cond
            (> ping 5000) skill-failed
            (not (warrior? id)) skill-failed
            (not (enemy? id selected-player-id)) skill-failed
            (not (alive? player-world-state)) skill-failed
            (not (alive? other-player-world-state)) skill-failed
            (not (enough-mana? skill player-world-state)) not-enough-mana
            (not (cooldown-finished? skill player)) skill-failed
            (not (close-for-attack? player-world-state other-player-world-state)) too-far
            :else (let [required-mana (get-required-mana skill)
                        ;; TODO update damage, player might have defense or poison etc.
                        damage ((-> common.skills/skills (get skill) :damage-fn)
                                (has-defense? selected-player-id)
                                (has-break-defense? selected-player-id))
                        health-after-damage (- (:health other-player-world-state) damage)
                        health-after-damage (Math/max ^long health-after-damage 0)]
                    (swap! world (fn [world]
                                   (-> world
                                       (update-in [id :mana] - required-mana)
                                       (assoc-in [selected-player-id :health] health-after-damage))))
                    (add-effect :attack-one-hand selected-player-id)
                    (make-asas-appear-if-hidden selected-player-id)
                    (swap! players assoc-in [id :last-time :skill skill] (now))
                    (process-if-death id selected-player-id health-after-damage players*)
                    (send! selected-player-id :got-attack-one-hand-damage {:damage damage
                                                                           :player-id id})
                    {:skill skill
                     :damage damage
                     :selected-player-id selected-player-id})))))))

(defmethod apply-skill "attackSlowDown" [{:keys [id ping] {:keys [skill selected-player-id]} :data}]
  (let [players* @players]
    (when-let [player (get players* id)]
      (when (get players* selected-player-id)
        (let [w @world
              player-world-state (get w id)
              other-player-world-state (get w selected-player-id)]
          (cond
            (> ping 5000) skill-failed
            (not (warrior? id)) skill-failed
            (not (alive? player-world-state)) skill-failed
            (not (alive? other-player-world-state)) skill-failed
            (not (enemy? id selected-player-id)) skill-failed
            (not (enough-mana? skill player-world-state)) not-enough-mana
            (not (cooldown-finished? skill player)) skill-failed
            (not (close-for-attack? player-world-state other-player-world-state)) too-far
            :else (let [required-mana (get-required-mana skill)
                        ;; TODO update damage, player might have defense or poison etc.
                        damage ((-> common.skills/skills (get skill) :damage-fn)
                                (has-defense? selected-player-id)
                                (has-break-defense? selected-player-id))
                        health-after-damage (- (:health other-player-world-state) damage)
                        health-after-damage (Math/max ^long health-after-damage 0)
                        slow-down? (prob? 0.5)]
                    (swap! world (fn [world]
                                   (-> world
                                       (update-in [id :mana] - required-mana)
                                       (assoc-in [selected-player-id :health] health-after-damage))))
                    (swap! players assoc-in [id :last-time :skill skill] (now))
                    (process-if-death id selected-player-id health-after-damage players*)
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

(defmethod apply-skill "shieldWall" [{:keys [id ping] {:keys [skill]} :data}]
  (when-let [player (get @players id)]
    (when-let [world-state (get @world id)]
      (cond
        (> ping 5000) skill-failed
        (not (warrior? id)) skill-failed
        (not (alive? world-state)) skill-failed
        (not (enough-mana? skill world-state)) not-enough-mana
        (not (cooldown-finished? skill player)) skill-failed
        :else (let [required-mana (get-required-mana skill)
                    _ (when-let [task (get-in @players [id :effects :shield-wall :task])]
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

(defmethod apply-skill "attackR" [{:keys [id ping] {:keys [skill selected-player-id]} :data}]
  (let [players* @players]
    (when-let [player (get players* id)]
      (when (get players* selected-player-id)
        (let [w @world
              player-world-state (get w id)
              other-player-world-state (get w selected-player-id)]
          (cond
            (> ping 5000) skill-failed
            (not (alive? player-world-state)) skill-failed
            (not (alive? other-player-world-state)) skill-failed
            (not (enemy? id selected-player-id)) skill-failed
            (not (enough-mana? skill player-world-state)) not-enough-mana
            (not (cooldown-finished? skill player)) skill-failed
            (not (close-for-attack? player-world-state other-player-world-state)) too-far
            :else (let [required-mana (get-required-mana skill)
                        ;; TODO update damage, player might have defense or poison etc.
                        damage ((-> common.skills/skills (get skill) :damage-fn)
                                (has-defense? selected-player-id)
                                (has-break-defense? selected-player-id))
                        health-after-damage (- (:health other-player-world-state) damage)
                        health-after-damage (Math/max ^long health-after-damage 0)]
                    (swap! world (fn [world]
                                   (-> world
                                       (update-in [id :mana] - required-mana)
                                       (assoc-in [selected-player-id :health] health-after-damage))))
                    (swap! players assoc-in [id :last-time :skill skill] (now))
                    (add-effect :attack-r selected-player-id)
                    (make-asas-appear-if-hidden selected-player-id)
                    (make-asas-appear-if-hidden id)
                    (process-if-death id selected-player-id health-after-damage players*)
                    (send! selected-player-id :got-attack-r-damage {:damage damage
                                                                    :player-id id})
                    {:skill skill
                     :damage damage
                     :selected-player-id selected-player-id})))))))

;; TODO scheduler'da ilerideki bir taski iptal etmenin yolunu bul, ornegin adam posion yedi ama posion gecene kadar cure aldi gibi...
;; TODO ADJUST COOLDOWN FOR ASAS CLASS
(defmethod apply-skill "fleetFoot" [{:keys [id ping] {:keys [skill]} :data}]
  (when-let [player (get @players id)]
    (when-let [world-state (get @world id)]
      (cond
        (> ping 5000) skill-failed
        (not (alive? world-state)) skill-failed
        (not (enough-mana? skill world-state)) not-enough-mana
        (not (cooldown-finished? skill player)) skill-failed
        :else (let [required-mana (get-required-mana skill)
                    effect-duration-kw (if (asas? id) :effect-duration-asas :effect-duration)]
                (swap! world update-in [id :mana] - required-mana)
                (swap! players assoc-in [id :last-time :skill skill] (now))
                (add-effect :fleet-foot id)
                (tea/after! (-> common.skills/skills (get skill) effect-duration-kw (/ 1000))
                            (bound-fn []
                              (when (get @players id)
                                (send! id :fleet-foot-finished true))))
                {:skill skill})))))

(defmethod apply-skill "hpPotion" [{:keys [id ping] {:keys [skill]} :data}]
  (when-let [player (get @players id)]
    (when-let [world-state (get @world id)]
      (cond
        (> ping 5000) skill-failed
        (not (alive? world-state)) skill-failed
        (not (cooldown-finished? skill player)) skill-failed
        (not (cooldown-finished? "mpPotion" player)) skill-failed
        :else (let [total-health (get player :health)]
                (swap! world update-in [id :health] use-potion (-> common.skills/skills (get skill) :hp) total-health)
                (swap! players assoc-in [id :last-time :skill skill] (now))
                (add-effect :hp-potion id)
                {:skill skill})))))

(defmethod apply-skill "mpPotion" [{:keys [id ping] {:keys [skill]} :data}]
  (when-let [player (get @players id)]
    (when-let [world-state (get @world id)]
      (cond
        (> ping 5000) skill-failed
        (not (alive? world-state)) skill-failed
        (not (cooldown-finished? skill player)) skill-failed
        (not (cooldown-finished? "hpPotion" player)) skill-failed
        :else (let [total-mana (get player :mana)]
                (swap! world update-in [id :mana] use-potion (-> common.skills/skills (get skill) :mp) total-mana)
                (swap! players assoc-in [id :last-time :skill skill] (now))
                (add-effect :mp-potion id)
                {:skill skill})))))

(defmethod apply-skill "attackDagger" [{:keys [id ping] {:keys [skill selected-player-id]} :data}]
  (let [players* @players]
    (when-let [player (get players* id)]
      (when (get players* selected-player-id)
        (let [w @world
              player-world-state (get w id)
              other-player-world-state (get w selected-player-id)]
          (cond
            (> ping 5000) skill-failed
            (not (asas? id)) skill-failed
            (not (enemy? id selected-player-id)) skill-failed
            (not (alive? player-world-state)) skill-failed
            (not (alive? other-player-world-state)) skill-failed
            (not (enough-mana? skill player-world-state)) not-enough-mana
            (not (cooldown-finished? skill player)) skill-failed
            (not (close-for-attack? player-world-state other-player-world-state)) too-far
            :else (let [required-mana (get-required-mana skill)
                        ;; TODO update damage, player might have defense or poison etc.
                        damage ((-> common.skills/skills (get skill) :damage-fn)
                                (has-defense? selected-player-id)
                                (has-break-defense? selected-player-id))
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
                    (process-if-death id selected-player-id health-after-damage players*)
                    (send! selected-player-id :got-attack-dagger-damage {:damage damage
                                                                         :player-id id})
                    {:skill skill
                     :damage damage
                     :selected-player-id selected-player-id})))))))

;; write the same function of shieldWall defmethod but for asas class and for skill phantomVision
(defmethod apply-skill "phantomVision" [{:keys [id ping] {:keys [skill]} :data}]
  (when-let [player (get @players id)]
    (when-let [world-state (get @world id)]
      (cond
        (> ping 5000) skill-failed
        (not (asas? id)) skill-failed
        (not (alive? world-state)) skill-failed
        (not (enough-mana? skill world-state)) not-enough-mana
        (not (cooldown-finished? skill player)) skill-failed
        :else (let [required-mana (get-required-mana skill)
                    _ (when-let [task (get-in @players [id :effects :phantom-vision :task])]
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
                (swap! players (fn [players]
                                 (-> players
                                     (assoc-in [id :last-time :skill skill] (now))
                                     (assoc-in [id :effects :hide :result] true))))
                (add-effect :hide id)
                (tea/after! (-> common.skills/skills (get skill) :effect-duration (/ 1000))
                            (bound-fn []
                              (when (get @players id)
                                (send! id :hide-finished true)
                                (swap! players assoc-in [id :effects :hide :result] false)
                                (add-effect :appear id))))
                {:skill skill})))))

;; write a function like shieldWall defmethod but for priest class and for skill heal that increases selected ally health by 480
;; make sure selected player is an ally and use `ally?` function, also ally should be alive too
;; when selected-player-id i nil that means the player heals himself
(defmethod apply-skill "heal" [{:keys [id ping] {:keys [skill selected-player-id]} :data}]
  (let [players* @players]
    (when-let [player (get players* id)]
      (when-let [world-state (get @world id)]
        (when-let [other-player-world-state (get @world selected-player-id)]
          (cond
            (> ping 5000) skill-failed
            (not (priest? id)) skill-failed
            (not (ally? id selected-player-id)) skill-failed
            (not (alive? world-state)) skill-failed
            (not (alive? other-player-world-state)) skill-failed
            (not (enough-mana? skill world-state)) not-enough-mana
            (not (cooldown-finished? skill player)) skill-failed
            (and (not= id selected-player-id)
                 (not (close-for-priest-skills? world-state other-player-world-state))) too-far
            :else (let [required-mana (get-required-mana skill)
                        health (get other-player-world-state :health)
                        total-health (get-in players* [selected-player-id :health])
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
                     :selected-player-id selected-player-id})))))))

;; write a functione like heal but for cure skill that removes poison effect from selected ally
(defmethod apply-skill "cure" [{:keys [id ping] {:keys [skill selected-player-id]} :data}]
  (let [players* @players]
    (when-let [player (get players* id)]
      (when-let [world-state (get @world id)]
        (when-let [other-player-world-state (get @world selected-player-id)]
          (cond
            (> ping 5000) skill-failed
            (not (priest? id)) skill-failed
            (not (ally? id selected-player-id)) skill-failed
            (not (alive? world-state)) skill-failed
            (not (alive? other-player-world-state)) skill-failed
            (not (enough-mana? skill world-state)) not-enough-mana
            (not (cooldown-finished? skill player)) skill-failed
            (and (not= id selected-player-id)
                 (not (close-for-priest-skills? world-state other-player-world-state))) too-far
            :else (let [required-mana (get-required-mana skill)
                        _ (when-let [task (get-in players* [selected-player-id :effects :break-defense :task])]
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
                     :selected-player-id selected-player-id})))))))

;; write a function for "defenseBreak" skill that breaks the defense of the selected enemy by 25% for 10 seconds
;; make sure selected player is an enemy and use `enemy?` function, also enemy should be alive too
(defmethod apply-skill "breakDefense" [{:keys [id ping] {:keys [skill selected-player-id]} :data}]
  (let [players* @players]
    (when-let [player (get players* id)]
      (when-let [world-state (get @world id)]
        (when-let [other-player-world-state (get @world selected-player-id)]
          (cond
            (> ping 5000) skill-failed
            (not (priest? id)) skill-failed
            (not (enemy? id selected-player-id)) skill-failed
            (not (alive? world-state)) skill-failed
            (not (alive? other-player-world-state)) skill-failed
            (not (enough-mana? skill world-state)) not-enough-mana
            (not (cooldown-finished? skill player)) skill-failed
            (not (close-for-priest-skills? world-state other-player-world-state)) too-far
            :else (let [required-mana (get-required-mana skill)
                        _ (when-let [task (get-in players* [selected-player-id :effects :break-defense :task])]
                            (tea/cancel! task))
                        _ (when-let [task (get-in @players [id :effects :shield-wall :task])]
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
                     :selected-player-id selected-player-id})))))))

(defmethod apply-skill "attackRange" [{:keys [id ping] {:keys [skill x y z]} :data}]
  (let [players* @players
        w* @world]
    (when-let [player (get players* id)]
      (when-let [world-state (get w* id)]
        (cond
          (> ping 5000) skill-failed
          (not (mage? id)) skill-failed
          (not (alive? world-state)) skill-failed
          (not (enough-mana? skill world-state)) not-enough-mana
          (not (cooldown-finished? skill player)) skill-failed
          (not (attack-range-in-distance? world-state x y z)) skill-failed
          :else (let [required-mana (get-required-mana skill)
                      _ (swap! players assoc-in [id :last-time :skill skill] (now))
                      _ (swap! world update-in [id :mana] - required-mana)
                      _ (add-effect :attack-range {:id id
                                                   :x x
                                                   :y y
                                                   :z z})
                      damaged-enemies (doall
                                        (for [enemy-id (keys players*)
                                              :let [enemy-world-state (get w* enemy-id)]
                                              :when (and (enemy? id enemy-id)
                                                         (alive? enemy-world-state)
                                                         (inside-circle? (:px enemy-world-state) (:pz enemy-world-state) x z 2.25))]
                                          (let [damage ((-> common.skills/skills (get skill) :damage-fn)
                                                        (has-defense? enemy-id)
                                                        (has-break-defense? enemy-id))
                                                health-after-damage (- (:health enemy-world-state) damage)
                                                health-after-damage (Math/max ^long health-after-damage 0)]
                                            (make-asas-appear-if-hidden enemy-id)
                                            (swap! world assoc-in [enemy-id :health] health-after-damage)
                                            (process-if-death id enemy-id health-after-damage players*)
                                            (send! enemy-id :got-attack-range {:damage damage
                                                                               :player-id id})
                                            {:id enemy-id
                                             :damage damage})))]
                  {:skill skill
                   :x x
                   :y y
                   :z z
                   :damages damaged-enemies}))))))

(defmethod apply-skill "attackSingle" [{:keys [id ping] {:keys [skill selected-player-id]} :data}]
  (let [players* @players
        w* @world]
    (when-let [player (get players* id)]
      (when-let [world-state (get w* id)]
        (when-let [other-player-world-state (get w* selected-player-id)]
          (cond
            (> ping 5000) skill-failed
            (not (mage? id)) skill-failed
            (not (enemy? id selected-player-id)) skill-failed
            (not (alive? world-state)) skill-failed
            (not (alive? other-player-world-state)) skill-failed
            (not (enough-mana? skill world-state)) not-enough-mana
            (not (cooldown-finished? skill player)) skill-failed
            (not (close-for-attack-single? world-state other-player-world-state)) too-far
            :else (let [required-mana (get-required-mana skill)
                        _ (swap! players assoc-in [id :last-time :skill skill] (now))
                        _ (swap! world update-in [id :mana] - required-mana)
                        damage ((-> common.skills/skills (get skill) :damage-fn)
                                (has-defense? selected-player-id)
                                (has-break-defense? selected-player-id))
                        health-after-damage (- (:health other-player-world-state) damage)
                        health-after-damage (Math/max ^long health-after-damage 0)]
                    (make-asas-appear-if-hidden selected-player-id)
                    (process-if-death id selected-player-id health-after-damage players*)
                    (swap! world assoc-in [selected-player-id :health] health-after-damage)
                    (send! selected-player-id :got-attack-single {:damage damage
                                                                  :player-id id})
                    (add-effect :attack-single selected-player-id)
                    {:skill skill
                     :selected-player-id selected-player-id
                     :damage damage})))))))

(defn- already-in-the-party? [player other-player]
  (and (:party-id other-player)
       (= (:party-id player) (:party-id other-player))))

(defmethod apply-skill "teleport" [{:keys [id ping] {:keys [skill selected-player-id]} :data}]
  (let [players* @players
        w* @world]
    (when-let [player (get players* id)]
      (when-let [world-state (get w* id)]
        (when-let [other-player-world-state (get w* selected-player-id)]
          (cond
            (> ping 5000) skill-failed
            (not (mage? id)) skill-failed
            (not (ally? id selected-player-id)) skill-failed
            (not (alive? world-state)) skill-failed
            (not (alive? other-player-world-state)) skill-failed
            (not (enough-mana? skill world-state)) not-enough-mana
            (not (cooldown-finished? skill player)) skill-failed
            (not (already-in-the-party? player (get players* selected-player-id))) skill-failed
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
                    {:skill skill})))))))

(def re-spawn-failed {:error :re-spawn-failed})

(defn- re-spawn-duration-finished? [player]
  (> (- (now) (get-in player [:last-time :died])) common.skills/re-spawn-duration-in-milli-secs))

(reg-pro
  :re-spawn
  (fn [{:keys [id]}]
    (let [players* @players
          w* @world]
      (when-let [player (get players* id)]
        (when-let [world-state (get w* id)]
          (cond
            (alive? world-state) re-spawn-failed
            (not (re-spawn-duration-finished? player)) re-spawn-failed
            :else (let [effect-tasks (->> player :effects vals (keep :task))
                        new-pos (if (= "orc" (:race player)) (random-pos-for-orc) (random-pos-for-human))
                        [x y z] new-pos]
                    (doseq [t effect-tasks]
                      (tea/cancel! t))
                    (swap! players (fn [players]
                                     (-> players
                                         (utils/dissoc-in [id :effects])
                                         (utils/dissoc-in [id :last-time :skill]))))
                    (swap! world (fn [world]
                                   (-> world
                                       (assoc-in [id :health] (:health player))
                                       (assoc-in [id :mana] (:mana player))
                                       (assoc-in [id :st] "idle")
                                       (assoc-in [id :px] x)
                                       (assoc-in [id :py] y)
                                       (assoc-in [id :pz] z))))
                    {:pos new-pos
                     :health (:health player)
                     :mana (:mana player)})))))))

(comment

  ;; make all players die
  ;; also set :last-time :died to now
  (do

    (swap! world (fn [world]
                   (reduce (fn [world id]
                             (-> world
                               (assoc-in [id :health] 0)))
                     world
                     (keys @players))))

    (swap! players (fn [players]
                     (reduce
                       (fn [players id]
                         (assoc-in players [id :last-time :died] (now)))
                       players
                       (keys players))))
    )


  (clojure.pprint/pprint @players)

  (reset-states)
  ;; set everyones health to 1600 in world atom
  ;; move above to a function using defn
  (swap! world (fn [world]
                 (reduce (fn [world id]
                           (assoc-in world [id :health] 20))
                   world
                   (keys @players))))

  ;;remove party from all players also make party-leader? false if true
  (swap! players (fn [players]
                   (reduce (fn [players id]
                             (-> players
                               (assoc-in [id :party-id] nil)
                               (assoc-in [id :party-leader?] false)))
                     players
                     (keys players))))


  (clojure.pprint/pprint @players)

  (clojure.pprint/pprint @world)
  (swap! world assoc-in [4 :pz] -39.04)

  (mount/start)
  (mount/stop)
  )

(reg-pro
  :skill
  (fn [opts]
    (try
      (apply-skill opts)
      (catch Throwable e
        (log/error e
                   "Something went wrong when applying skill."
                   (pr-str (select-keys opts [:id :data :ping]))
                   (get @players (:id opts)))
        skill-failed))))

(defmulti process-party-request (fn [{:keys [data]}]
                                  (:type data)))

(defn party-full? [player players]
  (let [party-id (:party-id player)]
    (and party-id
         (= (count (filter #(= party-id (:party-id %)) (vals players))) max-number-of-party-members))))

(defn leader? [player]
  (:party-leader? player))

(defn already-in-another-party? [player other-player]
  (and (:party-id other-player)
       (not= (:party-id player) (:party-id other-player))))

(defn blocked-party-requests? [other-player]
  (:party-requests-blocked? other-player))

(defn- asked-to-join-too-often? [id selected-player-id]
  (let [last-time (get-in @players [id :last-time :add-to-party-request selected-player-id])]
    (and (not (nil? last-time))
         (<= (- (now) last-time) common.skills/party-request-duration-in-milli-secs))))

(defmethod process-party-request :add-to-party [{:keys [id ping] {:keys [selected-player-id]} :data}]
  (let [players* @players]
    (when-let [player (get players* id)]
      (when-let [other-player (get players* selected-player-id)]
        (cond
          (> ping 5000) party-request-failed
          (= id selected-player-id) party-request-failed
          (not (ally? id selected-player-id)) party-request-failed
          (party-full? player players*) party-request-failed
          (already-in-the-party? player other-player) party-request-failed
          (already-in-another-party? player other-player) party-request-failed
          ;; TODO implement later
          (blocked-party-requests? other-player) party-request-failed
          (asked-to-join-too-often? id selected-player-id) party-request-failed
          :else (do
                  (swap! players assoc-in [id :last-time :add-to-party-request selected-player-id] (now))
                  (send! selected-player-id :party {:type :party-request
                                                    :player-id id})
                  {:type :add-to-party
                   :selected-player-id selected-player-id}))))))

;; TODO bug, human exit yapinca orc'ta partyden cikti diyor
(defn- remove-from-party [{:keys [players* id selected-player-id exit?]}]
  (when (not (and exit? (not (get-in players* [id :party-id]))))
    (let [player (get players* id)
          party-id (:party-id player)
          only-2-players-left? (= 2 (count (filter #(= party-id (:party-id %)) (vals players*))))
          party-cancelled? (or (= id selected-player-id) only-2-players-left?)
          party-member-ids (->> players*
                                (filter (fn [[_ v]]
                                          (= party-id (:party-id v))))
                                (map first)
                                (set))]
      (if (or (= id selected-player-id)
              (and exit? (leader? player)))
        (do
          (swap! players (fn [players]
                           (reduce
                             (fn [players [player-id attrs]]
                               (if (= party-id (:party-id attrs))
                                 (cond-> (assoc-in players [player-id :party-id] nil)
                                   (= player-id id) (assoc-in [player-id :party-leader?] false))
                                 players)) players players)))
          (doseq [[player-id attrs] players*
                  :when (and (not= id player-id)
                             (= party-id (:party-id attrs)))]
            (send! player-id :party {:type :party-cancelled})))
        (do
          (when only-2-players-left?
            (swap! players (fn [players]
                             (-> players
                                 (assoc-in [id :party-id] nil)
                                 (assoc-in [id :party-leader?] false)))))
          (when selected-player-id
            (swap! players assoc-in [selected-player-id :party-id] nil)
            (doseq [id (disj party-member-ids id)]
              (send! id :party {:type :remove-from-party
                                :player-id selected-player-id})))
          (when exit?
            (swap! players assoc-in [id :party-id] nil)
            (doseq [id* (disj party-member-ids id)]
              (send! id* :party {:type :member-exit-from-party
                                 :player-id id
                                 :username (get-in players* [id :username])})))))
      (if exit?
        {:type :exit-from-party}
        {:type :remove-from-party
         :player-id selected-player-id
         :party-cancelled? party-cancelled?}))))

(defmethod process-party-request :remove-from-party [{:keys [id ping] {:keys [selected-player-id]} :data}]
  (let [players* @players]
    (when-let [player (get players* id)]
      (when-let [other-player (get players* selected-player-id)]
        (cond
          (> ping 5000) party-request-failed
          (not (leader? player)) party-request-failed
          (not (already-in-the-party? player other-player)) party-request-failed
          :else (remove-from-party {:players* players*
                                    :player player
                                    :id id
                                    :selected-player-id selected-player-id}))))))

(defmethod process-party-request :exit-from-party [{:keys [id ping]}]
  (let [players* @players]
    (when-let [player (get players* id)]
      (cond
        (> ping 5000) party-request-failed
        (not (:party-id player)) party-request-failed
        :else (remove-from-party {:id id
                                  :players* players*
                                  :exit? true})))))

(defn- accepts-or-rejects-party-request-on-time? [other-player id]
  (let [last-time (get-in other-player [:last-time :add-to-party-request id])]
    (and last-time (<= (- (now) last-time) common.skills/party-request-duration-in-milli-secs))))

(defmethod process-party-request :accept-party-request [{:keys [id ping] {:keys [requested-player-id]} :data}]
  (let [players* @players]
    (when-let [player (get players* id)]
      (when-let [other-player (get players* requested-player-id)]
        (cond
          (> ping 5000) party-request-failed
          (not (accepts-or-rejects-party-request-on-time? other-player id)) party-request-failed
          (party-full? other-player players*) party-request-failed
          (already-in-another-party? other-player player) party-request-failed
          (already-in-the-party? other-player player) party-request-failed
          (blocked-party-requests? other-player) party-request-failed
          :else (let [party-id (or (:party-id other-player) (swap! party-id-generator inc))]
                  (swap! players (fn [players]
                                   (-> players
                                       (assoc-in [requested-player-id :party-id] party-id)
                                       (assoc-in [requested-player-id :party-leader?] true)
                                       (assoc-in [id :party-id] party-id)
                                       (assoc-in [id :last-time :accepted-party] (now)))))

                  (send! requested-player-id :party {:type :accepted-party-request
                                                     :player-id id})
                  (doseq [[player-id attrs] @players
                          :when (and (not= id player-id)
                                     (not= requested-player-id player-id)
                                     (= party-id (:party-id attrs)))]
                    (send! player-id :party {:type :joined-party
                                             :player-id id}))
                  {:type :accept-party-request
                   :party-members-ids (cons requested-player-id
                                            (->> @players
                                                 (filter (fn [[_ attrs]]
                                                           (and (= party-id (:party-id attrs)) (not (leader? attrs)))))
                                                 (sort-by (comp :accepted-party :last-time second))
                                                 (map first)))}))))))

(defmethod process-party-request :reject-party-request [{:keys [id ping] {:keys [requested-player-id]} :data}]
  (let [players* @players]
    (when (get players* id)
      (when-let [other-player (get players* requested-player-id)]
        (cond
          (> ping 5000) party-request-failed
          (not (accepts-or-rejects-party-request-on-time? other-player id)) party-request-failed
          :else (do
                  (send! requested-player-id :party {:type :party-request-rejected
                                                     :player-id id})
                  {:type :reject-party-request
                   :requested-player-id requested-player-id}))))))

(reg-pro
  :party
  (fn [opts]
    (try
      (process-party-request opts)
      (catch Throwable e
        (log/error e
                   "Something went wrong when applying party request."
                   (pr-str (select-keys opts [:id :data :ping]))
                   (get @players (:id opts)))
        party-request-failed))))

(defn- reset-states []
  (reset! world {})
  (reset! players {})
  (reset! id-generator 0))

(defn- ws-handler
  [req]
  (-> (http/websocket-connection req)
      (d/chain
        (fn [socket]
          (let [player-id (swap! id-generator inc)]
            (alter-meta! socket assoc :id player-id)
            (swap! players assoc-in [player-id :socket] socket)
            (s/on-closed socket
                         (fn []
                           (remove-from-party {:id player-id
                                               :players* @players
                                               :exit? true})
                           (swap! players dissoc player-id)
                           (swap! world dissoc player-id)
                           ;; TODO update for party members
                           (future
                             ;; TODO optimize in here, in between other players could attack etc. and update non existed player's state
                             ;; like check again after 5 secs or so...
                             (Thread/sleep 1000)
                             (swap! world dissoc player-id)
                             (notify-players-for-exit player-id)))))
          (s/consume
            (fn [payload]
              (try
                (let [id (-> socket meta :id)
                      now (Instant/now)
                      payload (msg/unpack payload)
                      ping (- (.toEpochMilli now) (:timestamp payload))]
                  (dispatch (:pro payload) {:id id
                                            :data (:data payload)
                                            :ping ping
                                            :req req
                                            :socket socket
                                            :send-fn (fn [socket {:keys [id result]}]
                                                       (when result
                                                         (s/put! socket (msg/pack (hash-map id result)))))}))
                (catch Exception e
                  (log/error e))))
            socket)))))

(defn home-routes
  []
  [""
   {:middleware [middleware/wrap-formats]}
   ["/" {:get home-page}]
   ["/ws" {:get ws-handler}]])
