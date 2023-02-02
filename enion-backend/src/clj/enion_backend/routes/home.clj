(ns enion-backend.routes.home
  (:require
   [aleph.http :as http]
   [clojure.java.io :as io]
   [clojure.tools.logging :as log]
   [common.enion.skills :as common.skills]
   [enion-backend.layout :as layout]
   [enion-backend.middleware :as middleware]
   [manifold.deferred :as d]
   [manifold.stream :as s]
   [mount.core :as mount :refer [defstate]]
   [msgpack.clojure-extensions]
   [msgpack.core :as msg]
   [procedure.async :refer [dispatch reg-pro]]
   [ring.util.http-response :as response]
   [enion-backend.teatime :as tea]
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
  ;; Simulating latency...
  (Thread/sleep (rand-between 50 100))
  (let [w @world]
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
  [(+ 38 (rand 1)) 0.55 (- (+ 39 (rand 4)))])

(defn random-pos-for-human
  []
  [(- (+ 38 (rand 5.5))) 0.55 (+ 39 (rand 1.5))])

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

(defn now []
  (.toEpochMilli (Instant/now)))

(defn- cooldown-finished? [skill player]
  (let [cooldown (-> common.skills/skills (get skill) :cooldown)
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

(defn- close-for-attack? [player-world-state other-player-world-state]
  (let [x (:px player-world-state)
        y (:py player-world-state)
        z (:pz player-world-state)
        x1 (:px other-player-world-state)
        y1 (:py other-player-world-state)
        z1 (:pz other-player-world-state)]
    (<= (distance x x1 y y1 z z1) (+ common.skills/close-attack-distance-threshold 0.25))))

(defmethod apply-skill "attackOneHand" [{:keys [id ping] {:keys [skill selected-player-id]} :data}]
  (when-let [player (get @players id)]
    (when (get @players selected-player-id)
      (let [w @world
            player-world-state (get w id)
            other-player-world-state (get w selected-player-id)]
        (cond
          (> ping 5000) skill-failed
          (not (alive? player-world-state)) skill-failed
          (not (alive? other-player-world-state)) skill-failed
          (not (enough-mana? skill player-world-state)) not-enough-mana
          (not (cooldown-finished? skill player)) skill-failed
          (not (close-for-attack? player-world-state other-player-world-state)) too-far
          :else (let [required-mana (get-required-mana skill)
                      ;; TODO update damage, player might have defense or poison etc.
                      damage ((-> common.skills/skills (get skill) :damage-fn))
                      health-after-damage (- (:health other-player-world-state) damage)
                      health-after-damage (Math/max ^long health-after-damage 0)]
                  (swap! world (fn [world]
                                 (-> world
                                   (update-in [id :mana] - required-mana)
                                   (assoc-in [selected-player-id :health] health-after-damage))))
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
          (not (alive? player-world-state)) skill-failed
          (not (alive? other-player-world-state)) skill-failed
          (not (enough-mana? skill player-world-state)) not-enough-mana
          (not (cooldown-finished? skill player)) skill-failed
          (not (close-for-attack? player-world-state other-player-world-state)) too-far
          :else (let [required-mana (get-required-mana skill)
                      ;; TODO update damage, player might have defense or poison etc.
                      damage ((-> common.skills/skills (get skill) :damage-fn))
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
                  ;;TODO add scheduler for prob cure
                  (send! selected-player-id :got-attack-slow-down-damage {:damage damage
                                                                          :player-id id
                                                                          :slow-down? slow-down?})
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
          (not (enough-mana? skill player-world-state)) not-enough-mana
          (not (cooldown-finished? skill player)) skill-failed
          (not (close-for-attack? player-world-state other-player-world-state)) too-far
          :else (let [required-mana (get-required-mana skill)
                      ;; TODO update damage, player might have defense or poison etc.
                      damage ((-> common.skills/skills (get skill) :damage-fn))
                      health-after-damage (- (:health other-player-world-state) damage)
                      health-after-damage (Math/max ^long health-after-damage 0)]
                  (swap! world (fn [world]
                                 (-> world
                                   (update-in [id :mana] - required-mana)
                                   (assoc-in [selected-player-id :health] health-after-damage))))
                  (swap! players assoc-in [id :last-time :skill skill] (now))
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
    (let [world-state (get @world id)]
      (cond
        (> ping 5000) skill-failed
        (not (alive? world-state)) skill-failed
        (not (enough-mana? skill world-state)) not-enough-mana
        (not (cooldown-finished? skill player)) skill-failed
        :else (let [required-mana (get-required-mana skill)]
                (swap! world update-in [id :mana] - required-mana)
                (swap! players assoc-in [id :last-time :skill skill] (now))
                (tea/after! (-> common.skills/skills (get skill) :cooldown (- 1000) (/ 1000))
                                (bound-fn []
                                  (when (get @players id)
                                    (send! id :fleet-foot-finished true))))
                {:skill skill})))))

(defmethod apply-skill "hpPotion" [{:keys [id ping] {:keys [skill]} :data}]
  (when-let [player (get @players id)]
    (let [world-state (get @world id)]
      (cond
        (> ping 5000) skill-failed
        (not (alive? world-state)) skill-failed
        (not (cooldown-finished? skill player)) skill-failed
        :else (let [total-health (get player :health)]
                (swap! world update-in [id :health] use-potion (-> common.skills/skills (get skill) :hp) total-health)
                (swap! players assoc-in [id :last-time :skill skill] (now))
                {:skill skill})))))

(defmethod apply-skill "mpPotion" [{:keys [id ping] {:keys [skill]} :data}]
  (when-let [player (get @players id)]
    (let [world-state (get @world id)]
      (cond
        (> ping 5000) skill-failed
        (not (alive? world-state)) skill-failed
        (not (cooldown-finished? skill player)) skill-failed
        :else (let [total-mana (get player :mana)]
                (swap! world update-in [id :mana] use-potion (-> common.skills/skills (get skill) :mp) total-mana)
                (swap! players assoc-in [id :last-time :skill skill] (now))
                {:skill skill})))))

(comment
  @world
  (swap! world assoc-in [76 :health] 900)
  (swap! world assoc-in [76 :mana] 0)
  (clojure.pprint/pprint @players)
  (clojure.pprint/pprint @world)

  (reset-states)
  @world
  @players
  (swap! world assoc-in [29 :mana] 1000)
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
