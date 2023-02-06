(ns enion-backend.routes.home
  (:require
    [aleph.http :as http]
    [clojure.java.io :as io]
    [clojure.tools.logging :as log]
    [common.enion.skills :as common.skills]
    [enion-backend.layout :as layout]
    [enion-backend.middleware :as middleware]
    [enion-backend.teatime :as tea]
    [manifold.deferred :as d]
    [manifold.stream :as s]
    [mount.core :as mount :refer [defstate]]
    [msgpack.clojure-extensions]
    [msgpack.core :as msg]
    [procedure.async :refer [dispatch reg-pro]]
    [ring.util.http-response :as response]
    [ring.util.response])
  (:import
    (java.time
      Instant)
    (java.util.concurrent
      ExecutorService
      Executors
      TimeUnit)))

(def max-number-of-players 50)
(def max-number-of-same-race-players (/ max-number-of-players 2))

(def send-snapshot-count 20)
(def world-tick-rate (/ 1000 send-snapshot-count))

(defonce id-generator (atom 0))
(defonce players (atom {}))
(defonce world (atom {}))

(defonce effects-stream (s/stream))

(defn- add-effect [effect target-id]
  (s/put! effects-stream {:effect effect
                          :target-id target-id}))

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
           (assoc acc k (vec (distinct (map :target-id v))))) {})))

(defn home-page
  [request]
  (layout/render request "home.html" {:docs (-> "docs/docs.md" io/resource slurp)}))

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
        w @world
        w (if (empty? effects) w (assoc w :effects effects))]
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
  :spawn
  (fn [_]
    (random-pos-for-human)))

(reg-pro
  :set-state
  (fn [{:keys [id data]}]
    ;; TODO gets the data right away, need to select keys...to prevent hacks
    (swap! world update id merge data)
    nil))

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

(reg-pro
  :init
  (fn [{id :id {:keys [username race class]} :data}]
    (println "Player joining...")
    (let [pos (if (= "orc" race)
                (random-pos-for-orc)
                (random-pos-for-human))
          health (get-in common.skills/classes [class :health])
          mana (get-in common.skills/classes [class :mana])
          attrs {:id id
                 :username username
                 ;; :race race
                 :race (if (odd? id) "human" "orc")
                 :class class
                 :health health
                 :mana mana
                 :pos pos}]
      (swap! players update id merge attrs)
      (notify-players-for-new-join id attrs)
      attrs)))

(comment
  (clojure.pprint/pprint @players)
  (send! 49 :init {:username "0000000"
                   :race "orc"
                   :class "asas"})
  )

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

(defn now []
  (.toEpochMilli (Instant/now)))

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
  (close-distance? player-world-state other-player-world-state common.skills/priest-skills-distance-threshold))

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

;; TODO make asas hide false when he gets damage
(defmethod apply-skill "attackOneHand" [{:keys [id ping] {:keys [skill selected-player-id]} :data}]
  (when-let [player (get @players id)]
    (when (get @players selected-player-id)
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
                  (when (= 0 health-after-damage)
                    (swap! players assoc-in [id :last-time :died] (now)))
                  (send! selected-player-id :got-attack-one-hand-damage {:damage damage
                                                                         :player-id id})
                  {:skill skill
                   :damage damage
                   :selected-player-id selected-player-id}))))))

(defmethod apply-skill "attackSlowDown" [{:keys [id ping] {:keys [skill selected-player-id]} :data}]
  (when-let [player (get @players id)]
    (when (get @players selected-player-id)
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
                  (when (= 0 health-after-damage)
                    (swap! players assoc-in [id :last-time :died] (now)))
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
                   :selected-player-id selected-player-id}))))))

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
  (when-let [player (get @players id)]
    (when (get @players selected-player-id)
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
                  (when (= 0 health-after-damage)
                    (swap! players assoc-in [id :last-time :died] (now)))
                  (send! selected-player-id :got-attack-r-damage {:damage damage
                                                                  :player-id id})
                  {:skill skill
                   :damage damage
                   :selected-player-id selected-player-id}))))))

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
  (when-let [player (get @players id)]
    (when (get @players selected-player-id)
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
                  (when (= 0 health-after-damage)
                    (swap! players assoc-in [id :last-time :died] (now)))
                  (send! selected-player-id :got-attack-dagger-damage {:damage damage
                                                                       :player-id id})
                  {:skill skill
                   :damage damage
                   :selected-player-id selected-player-id}))))))

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

(comment
  ;; set everyones health to 1600 in world atom
  ;; move above to a function using defn
  (swap! world (fn [world]
                 (reduce (fn [world id]
                           (assoc-in world [id :health] 1600))
                   world
                   (keys @players))))

  (send! id :phantom-vision-finished true)

  (clojure.pprint/pprint @players)

  (clojure.pprint/pprint @world)

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
                           (swap! players dissoc player-id)
                           (future
                             ;; TODO optimize in here, in between other players could attack etc. and update non existed player's state
                             ;; like check again after 5 secs or so...
                             (Thread/sleep 1000)
                             (swap! world dissoc player-id)
                             (notify-players-for-exit player-id)))))
          ;; TODO register socket in here
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
   {:middleware [middleware/wrap-csrf
                 middleware/wrap-formats]}
   ["/" {:get home-page}]
   ["/ws" {:get ws-handler}]])
