(ns enion-backend.routes.home
  (:require
    [aleph.http :as http]
    [clojure.edn :as edn]
    [clojure.java.io :as io]
    [clojure.set :as set]
    [clojure.string :as str]
    [clojure.tools.logging :as log]
    [common.enion.item :as common.item]
    [common.enion.skills :as common.skills]
    [enion-backend.async :as easync :refer [dispatch dispatch-in reg-pro]]
    [enion-backend.layout :as layout]
    [enion-backend.middleware :as middleware]
    [enion-backend.npc.core :as npc]
    [enion-backend.npc.drop :as npc.drop]
    [enion-backend.redis :as redis]
    [enion-backend.teatime :as tea]
    [enion-backend.utils :as utils :refer [dev?]]
    [manifold.deferred :as d]
    [manifold.stream :as s]
    [mount.core :as mount :refer [defstate]]
    [msgpack.clojure-extensions]
    [msgpack.core :as msg]
    [nano-id.core :refer [nano-id]]
    [ring.util.http-response :as response]
    [ring.util.response]
    [sentry-clj.core :as sentry])
  (:import
    (java.time
      Instant)
    (java.util.concurrent
      ExecutorService
      Executors
      TimeUnit)))

(def usernames-map (edn/read-string (slurp (io/resource "usernames.edn"))))

(def battle-points-by-party-size
  {1 72
   2 36
   3 24
   4 20
   5 15})

(def max-number-of-players 40)
(def max-number-of-same-race-players (/ max-number-of-players 2))

(def send-snapshot-count 20)
(def world-tick-rate (/ 1000 send-snapshot-count))

(def afk-threshold-in-milli-secs (* 3 60 1000))

(def max-number-of-party-members 5)

(defonce players (atom {}))
(defonce world (atom {}))

(defonce effects-stream (s/stream))
(defonce npc-effects-stream (s/stream))
(defonce killings-stream (s/stream))

(defn now []
  (.toEpochMilli (Instant/now)))

(defn add-effect [effect data]
  (s/put! effects-stream {:effect effect
                          :data data}))

(defn add-effect-to-npc [effect data]
  (s/put! npc-effects-stream {:effect effect
                              :data data}))

(defn add-killing [killer-id killed-id]
  (s/put! killings-stream {:killer-id killer-id
                           :killed-id killed-id}))

(defn take-while-stream [pred stream]
  (loop [result []]
    (let [value @(s/try-take! stream 0)]
      (if (pred value)
        (recur (conj result value))
        result))))

(defn create-effects->player-ids-mapping [effects]
  (->> effects
       (group-by :effect)
       (reduce-kv
         (fn [acc k v]
           (assoc acc k (vec (distinct (map :data v))))) {})))

(defn home-page
  [request]
  (layout/render request "index.html"))

(defn send!
  [player-id pro-id result]
  ;; TODO check if socket closed or not!
  (when result
    (when-let [socket (get-in @players [player-id :socket])]
      (s/put! socket (msg/pack (hash-map pro-id result))))))

(def comp-not-nil (comp not nil?))

(defn- get-players-with-defense [players]
  (->> players
       (filter
         (fn [[_ player]]
           (and (get player :party-id)
                (get-in player [:effects :break-defense :result]))))
       (map (fn [[_ player]]
              {:id (:id player)
               :party-id (:party-id player)}))
       (group-by :party-id)
       (reduce-kv
         (fn [acc k v]
           (assoc acc k (vec (map :id v)))) {})))

(defn notify-players-for-exit [id username]
  (doseq [other-player-id (filter #(not= id %) (keys @world))]
    (println "Sending exit notification...")
    (send! other-player-id :player-exit id)
    (when-not (str/blank? username)
      (send! other-player-id :global-message {:id :server
                                              :msg (str username " left")}))))

(comment
  (mount/stop)
  (mount/start)

  (redis/get "abc")
  npc/npcs
  )

(defn- send-world-snapshots* []
  (try
    (let [effects (->> effects-stream
                       (take-while-stream comp-not-nil)
                       create-effects->player-ids-mapping)
          npc-effects (->> npc-effects-stream
                           (take-while-stream comp-not-nil)
                           create-effects->player-ids-mapping)
          kills (take-while-stream comp-not-nil killings-stream)
          w @world
          current-players @players
          w (if (empty? effects) w (assoc w :effects effects))
          w (if (empty? kills) w (assoc w :kills kills))
          w (if (empty? npc-effects) w (assoc w :npc-effects npc-effects))
          all-npcs (npc/update-all-npcs! w current-players)
          w (reduce-kv
              (fn [w id v]
                (assoc w id (if (= "asas" (get-in current-players [id :class]))
                              (assoc v :hide (get-in current-players [id :effects :hide :result]))
                              v)))
              w w)
          party-id->players (get-players-with-defense current-players)]
      (doseq [player-id (keys w)
              :let [party-id (get-in current-players [player-id :party-id])
                    w (if-let [members-ids (seq (get party-id->players party-id))]
                        (assoc w :break-defense (set members-ids))
                        w)
                    w (if (empty? all-npcs) w (assoc w :npcs (npc/filter-npcs-by-player w player-id all-npcs)))]]
        (send! player-id :world-snapshot w)))
    (catch Exception e
      (println e)
      ;; (log/error e)
      )))

(defmacro create-single-thread-executor [millisecs f]
  `(doto (Executors/newSingleThreadScheduledExecutor)
     (.scheduleAtFixedRate (if (dev?) (var ~f) ~f) 0 ~millisecs TimeUnit/MILLISECONDS)))

(defn- send-world-snapshots []
  (create-single-thread-executor world-tick-rate send-world-snapshots*))

(defn- check-afk-players* []
  (doseq [[_ player] @players
          :let [last-activity (get-in player [:last-time :activity])]
          :when last-activity]
    (try
      (when (>= (- (now) last-activity) afk-threshold-in-milli-secs)
        (println "Kicking AFK player... ")
        (s/close! (:socket player)))
      (catch Exception e
        (println "Couldn't kick AFK player")
        (log/error e)))))

(defn- check-afk-players []
  (when-not (dev?)
    (create-single-thread-executor 1000 check-afk-players*)))

(defn- check-enemies-entered-base* []
  (try
    (let [current-world @world
          current-players @players
          player-ids-to-damage (->> current-players
                                    (filter
                                      (fn [[_ player]]
                                        (and
                                          (get player :in-enemy-base?)
                                          (not (get-in player [:effects :hide :result]))
                                          (get current-world (:id player))
                                          (> (get-in current-world [(:id player) :health]) 0))))
                                    (map (fn [[_ player]]
                                           (:id player))))]
      (doseq [id player-ids-to-damage]
        (dispatch-in :skill {:id id
                             :data {:skill "baseDamage"}
                             :players current-players
                             :world current-world})))
    (catch Exception e
      (println "Couldn't check enemies entered base")
      (log/error e))))

(defn- check-leftover-player-states* []
  (let [player-ids-to-remove (->> @players
                                  (filter (fn [[_ p]] (str/blank? (:username p))))
                                  (map first))]
    (when (seq player-ids-to-remove)
      (swap! players #(apply dissoc (cons % player-ids-to-remove))))
    (when-let [player-ids-to-remove-from-world (seq (set/difference (set (keys @world)) (set (keys @players))))]
      (swap! world #(apply dissoc (cons % player-ids-to-remove-from-world))))))

(defn- restore-hp-&-mp-for-players-out-of-combat* []
  (try
    (dispatch-in :skill {:data {:skill "restoreHpMp"}
                         :players @players
                         :world @world})
    (catch Exception e
      (log/error e))))

(defn- check-enemies-entered-base []
  (create-single-thread-executor 1000 check-enemies-entered-base*))

(defn- check-leftover-player-states []
  (create-single-thread-executor (* 60 1000) check-leftover-player-states*))

(defn- restore-hp-&-mp-for-players-out-of-combat []
  (create-single-thread-executor 5000 restore-hp-&-mp-for-players-out-of-combat*))

(defn- shutdown [^ExecutorService ec]
  (.shutdown ec)
  (try
    (when-not (.awaitTermination ec 2 TimeUnit/SECONDS)
      (.shutdownNow ec))
    (catch InterruptedException _
      ;; TODO may no need interrupt fn
      (.. Thread currentThread interrupt)
      (.shutdownNow ec))))

(def selected-keys-of-set-state [:px :py :pz :ex :ey :ez :st])

(defn- update-last-activity-time [id]
  (when (get @players id)
    (swap! players assoc-in [id :last-time :activity] (now))))

(defn update-last-combat-time [& player-ids]
  (doseq [id player-ids]
    (when (get @players id)
      (swap! players assoc-in [id :last-time :combat] (now)))))

(reg-pro
  :set-state
  (fn [{:keys [id data]}]
    (swap! world update id merge (select-keys data selected-keys-of-set-state))
    (update-last-activity-time id)
    nil))

(reg-pro
  :ping
  (fn [{:keys [data]}]
    {:timestamp (:timestamp data)
     :online (count @world)}))

(reg-pro
  :set-ping
  (fn [{:keys [id data]}]
    (swap! players assoc-in [id :ping] (:ping data))
    nil))

(defn get-orcs [players]
  (filter (fn [[_ p]] (= "orc" (:race p))) players))

(defn get-humans [players]
  (filter (fn [[_ p]] (= "human" (:race p))) players))

(reg-pro
  :get-server-stats
  (fn [{:keys [current-players]}]
    (let [orcs (count (get-orcs current-players))
          humans (count (get-humans current-players))]
      {:number-of-players (+ orcs humans)
       :orcs orcs
       :humans humans
       :max-number-of-same-race-players max-number-of-same-race-players
       :max-number-of-players max-number-of-players})))

(reg-pro
  :get-score-board
  (fn [{:keys [id current-players]}]
    (update-last-activity-time id)
    (let [orcs (map (fn [[_ p]] (assoc p :bp (or (:bp p) 0))) (get-orcs current-players))
          humans (map (fn [[_ p]] (assoc p :bp (or (:bp p) 0))) (get-humans current-players))]
      (when-let [players* (seq (concat orcs humans))]
        (map #(select-keys % [:id :race :class :bp :level]) players*)))))

(defn find-player-ids-by-party-id [players party-id]
  (->> players
       (filter
         (fn [[_ v]]
           (= party-id (:party-id v))))
       (map first)))

(def message-sent-too-often-msg {:error :message-sent-too-often})

(defn message-sent-too-often? [player]
  (let [last-time (get-in player [:last-time :message-sent])
        now (now)]
    (and last-time (< (- now last-time) 1500))))

(reg-pro
  :send-global-message
  (fn [{:keys [id] {:keys [msg]} :data}]
    (update-last-activity-time id)
    (let [players* @players
          player (players* id)
          player-ids (keys @world)
          message-sent-too-often? (message-sent-too-often? player)]
      (swap! players assoc-in [id :last-time :message-sent] (now))
      (if message-sent-too-often?
        message-sent-too-often-msg
        (when-not (or (str/blank? msg)
                      (> (count msg) 80))
          (doseq [id* player-ids]
            (send! id* :global-message {:id id
                                        :msg msg})))))))

(reg-pro
  :send-party-message
  (fn [{:keys [id] {:keys [msg]} :data}]
    (update-last-activity-time id)
    (let [players* @players
          player (players* id)
          party-id (:party-id player)
          message-sent-too-often? (message-sent-too-often? player)]
      (swap! players assoc-in [id :last-time :message-sent] (now))
      (cond
        message-sent-too-often? message-sent-too-often-msg
        :else
        (when (not (or (str/blank? msg)
                       (> (count msg) 60)
                       (nil? party-id)))
          (doseq [id (find-player-ids-by-party-id players* party-id)]
            (send! id :party-message {:id id
                                      :msg msg})))))))

(defn notify-players-for-new-join [id attrs]
  (doseq [other-player-id (filter #(not= id %) (keys @world))]
    (println "Sending new join...")
    (send! other-player-id :player-join attrs)
    (send! other-player-id :global-message {:id :server
                                            :msg (str (:username attrs) " joined")})))

(def race-set #{"orc" "human"})
(def class-set #{"warrior" "mage" "asas" "priest"})

(defn get-usernames []
  (->> @players
       vals
       (keep :username)
       (map str/lower-case)
       set))

(defn human-race-full? []
  (= max-number-of-same-race-players (count (filter (fn [[_ p]] (= "human" (:race p))) @players))))

(defn orc-race-full? []
  (= max-number-of-same-race-players (count (filter (fn [[_ p]] (= "orc" (:race p))) @players))))

(defn full? []
  (let [players @players
        orcs (count (filter (fn [[_ p]] (= "orc" (:race p))) players))
        humans (count (filter (fn [[_ p]] (= "human" (:race p))) players))]
    (= max-number-of-players (+ orcs humans))))

(defn- find-least-repetitive-class [race current-players]
  (let [players (filter #(= race (:race %)) (vals current-players))]
    (or (first (shuffle (set/difference class-set (set (keep :class players)))))
        (->> players
             (group-by :class)
             (vals)
             (sort-by count)
             ffirst
             :class)
        (-> class-set shuffle first))))

(defn- generate-username [username race class current-players]
  (if (str/blank? username)
    (let [taken-usernames (->> current-players
                               (filter (fn [[_ p]] (= race (:race p))))
                               (map (fn [[_ p]] (:username p)))
                               set)]
      (-> (set/difference (usernames-map class) taken-usernames) shuffle first))
    username))

(comment
  #_(let [level 30
          class "warrior"]
      {:id id
       :data data
       :username (or (and username? username)
                     (some-> data (get class) :username)
                     username)
       :health (get-in common.skills/level->health-mana-table [level class :health])
       :mana (get-in common.skills/level->health-mana-table [level class :mana])
       :pos pos
       :attack-power (get common.skills/level->attack-power-table level)
       :race race
       :class class
       :level level
       :exp 0
       :required-exp 100000
       :token token})
  )

(reg-pro
  :finish-tutorial
  (fn [{:keys [id current-players] {:keys [tutorial]} :data}]
    (if (= tutorial :reset)
      (redis/reset-tutorial (get-in current-players [id :token]))
      (redis/complete-tutorial (get-in current-players [id :token]) tutorial))
    nil))

(reg-pro
  :finish-quest
  (fn [{:keys [id current-players] {:keys [quest coin]} :data}]
    (let [level (get-in current-players [id :level])
          token (get-in current-players [id :token])
          class (get-in current-players [id :class])
          total-coin (+ (get-in current-players [id :coin] 0) coin)
          new-level (inc level)
          attack-power (get common.skills/level->attack-power-table new-level)
          new-required-exp (get common.skills/level->exp-table new-level)
          {:keys [health mana]} (get-in common.skills/level->health-mana-table [new-level class])
          completed-quests (get-in current-players [id :quests])]
      (if (completed-quests quest)
        {:error :quest-already-completed}
        (do
          (redis/complete-quest token class quest)
          (if (< level 10)
            (do
              (swap! players (fn [players]
                               (-> players
                                   (assoc-in [id :exp] 0)
                                   (assoc-in [id :level] new-level)
                                   (assoc-in [id :health] health)
                                   (assoc-in [id :mana] mana)
                                   (assoc-in [id :required-exp] new-required-exp)
                                   (assoc-in [id :coin] total-coin))))
              (redis/level-up token class {:level new-level
                                           :exp 0
                                           :coin total-coin})
              {:quest quest
               :level new-level
               :exp 0
               :attack-power attack-power
               :required-exp new-required-exp
               :health health
               :mana mana
               :coin coin
               :total-coin total-coin})
            (do
              (swap! players assoc-in [id :coin] total-coin)
              {:coin coin
               :total-coin total-coin
               :quest quest})))))))

(defn- get-pos-for-player [race class data]
  (cond
    (or (nil? data)
        (not= #{:squid :ghoul :demon} (get-in data [class :quests])))
    (if (= "orc" race) [37.84 0.6 -42.58] [-39.01 0.6 40.36])
    (= "orc" race) (common.skills/random-pos-for-orc)
    :else (common.skills/random-pos-for-human)))

(reg-pro
  :init
  (fn [{:keys [id current-players] {:keys [username race class]} :data}]
    (cond
      (nil? (get current-players id)) {:error :something-went-wrong}
      (full?) {:error :server-full}
      (and (= race "human") (human-race-full?)) {:error :human-race-full}
      (and (= race "orc") (orc-race-full?)) {:error :orc-race-full}
      (and (not (str/blank? username))
           (not (common.skills/username? username))) {:error :invalid-username}
      (and (not (str/blank? username))
           (or ((get-usernames) (str/lower-case username))
               (#{"system" "server" "admin" "gm"} (str/lower-case username)))) {:error :username-taken}
      :else (try
              (println "Player joining...")
              (let [orcs-count (count (get-orcs current-players))
                    humans-count (count (get-humans current-players))
                    token (get-in current-players [id :token])
                    data (some-> token redis/get)
                    last-played-race (when-let [race (:last-played-race data)]
                                       (cond
                                         (and (= race "orc") (orc-race-full?)) "human"
                                         (and (= race "human") (human-race-full?)) "orc"
                                         (= race "orc") "orc"
                                         :else "human"))
                    race (or race
                             last-played-race
                             (cond
                               (human-race-full?) "orc"
                               (orc-race-full?) "human"
                               (< humans-count orcs-count) "human"
                               :else "orc"))
                    class (or class
                              (some-> data :last-played-class)
                              (find-least-repetitive-class race current-players))
                    pos (get-pos-for-player race class data)
                    username? (not (str/blank? username))
                    username (generate-username username race class current-players)
                    level 1
                    exp 0
                    bp 0
                    coin 0
                    level (or (some-> data (get class) :level) level)
                    {:keys [health mana]} (get-in common.skills/level->health-mana-table [level class])
                    quests (or (some-> data (get class) :quests) #{})
                    new-player? (str/blank? token)
                    token (if new-player? (nano-id) token)
                    _ (when data
                        (redis/update-last-played-class token class)
                        (redis/update-last-played-race token race))
                    _ (when (and username? data)
                        (redis/update-username token class username))
                    attrs {:id id
                           :data data
                           :username (or (and username? username)
                                         (some-> data (get class) :username)
                                         username)
                           :race race
                           :class class
                           :health health
                           :mana mana
                           :pos pos
                           :coin (or (some-> data (get class) :coin) coin)
                           :inventory (some-> data (get class) :inventory)
                           :equip (some-> data (get class) :equip)
                           :level level
                           :attack-power (get common.skills/level->attack-power-table level)
                           :required-exp (get common.skills/level->exp-table level)
                           :exp (or (some-> data (get class) :exp) exp)
                           :bp (or (some-> data (get class) :bp) bp)
                           :tutorials (or (some-> data :tutorials) #{})
                           :quests quests
                           :token token
                           :new-player? new-player?}]
                (swap! players update id merge attrs)
                (notify-players-for-new-join id attrs)
                attrs)
              (catch Exception e
                (println e)
                (log/error e)
                {:error :something-went-wrong})))))

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
    {:players (map #(select-keys % [:id :username :race :class :health :level :equip :mana :pos]) (vals @players))
     :npcs (npc/npc-types->ids)}))

(def ping-high {:error :ping-high})
(def skill-failed {:error :skill-failed})
(def pvp-locked {:error :pvp-locked})
(def enemy-low-level {:error :enemy-low-level})
(def too-far {:error :too-far})
(def not-enough-mana {:error :not-enough-mana})
(def party-request-failed {:error :party-request-failed})

(defn alive? [state]
  (> (:health state) 0))

(defn enough-mana? [skill state]
  (>= (:mana state) (-> common.skills/skills (get skill) :required-mana)))

(defn satisfies-level? [skill player]
  (>= (:level player) (-> common.skills/skills (get skill) :required-level)))

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

(defn cooldown-finished? [skill player]
  (let [cooldown (if (and (= skill "fleetFoot") (asas? (:id player)))
                   (-> common.skills/skills (get skill) :cooldown-asas)
                   (-> common.skills/skills (get skill) :cooldown))
        last-time-skill-used (get-in player [:last-time :skill skill])]
    (or (nil? last-time-skill-used)
        (>= (- (now) last-time-skill-used) cooldown))))

(defn get-required-mana [skill]
  (-> common.skills/skills (get skill) :required-mana))

(defn use-potion [health-or-mana value max]
  (Math/min ^long (+ health-or-mana value) ^long max))

(defn distance
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

(defn close-distance? [player-world-state other-player-world-state threshold]
  (let [x (:px player-world-state)
        y (:py player-world-state)
        z (:pz player-world-state)
        x1 (:px other-player-world-state)
        y1 (:py other-player-world-state)
        z1 (:pz other-player-world-state)]
    (<= (distance x x1 y y1 z z1) threshold)))

(defn close-npc-distance? [player-world-state npc-world-state threshold]
  (let [x (:px player-world-state)
        z (:pz player-world-state)
        [x1 z1] (:pos npc-world-state)]
    (<= (distance x x1 z z1) threshold)))

(defn close-for-attack? [player-world-state other-player-world-state]
  (close-distance? player-world-state other-player-world-state (+ common.skills/close-attack-distance-threshold 1)))

(defn close-for-attack-to-npc? [player-world-state npc-world-state]
  (close-npc-distance? player-world-state npc-world-state (+ common.skills/close-attack-distance-threshold 1)))

(defn close-for-priest-skill-attack-to-npc? [player-world-state npc-world-state]
  (close-npc-distance? player-world-state npc-world-state (+ common.skills/priest-skills-distance-threshold 1)))

(defn close-for-priest-skills? [player-world-state other-player-world-state]
  (close-distance? player-world-state other-player-world-state (+ common.skills/priest-skills-distance-threshold 1)))

(defn close-for-attack-single? [player-world-state other-player-world-state]
  (close-distance? player-world-state other-player-world-state (+ common.skills/attack-single-distance-threshold 1)))

(defn close-for-npc-attack-single? [player-world-state npc-world-state]
  (close-npc-distance? player-world-state npc-world-state (+ common.skills/attack-single-distance-threshold 1)))

(defn attack-range-in-distance? [world-state x y z]
  (try
    (let [{:keys [px py pz]} world-state]
      (<= (distance px x py y pz z) common.skills/attack-range-distance-threshold))
    (catch Exception e
      (let [{:keys [px py pz]} world-state]
        (log/error e (str "attack-range-in-distance failed, here params: " (pr-str [px x py y pz z])))
        (sentry/send-event {:message (pr-str {:world-state world-state
                                              :positions [px x py y pz z]})
                            :throwable e})))))

(defn hidden? [selected-player-id]
  (get-in @players [selected-player-id :effects :hide :result]))

(defn make-asas-appear-if-hidden [selected-player-id]
  (when (hidden? selected-player-id)
    (swap! players assoc-in [selected-player-id :effects :hide :result] false)
    (send! selected-player-id :hide-finished true)))

;; TODO replace @players -> players*
;; todo refactor here, duplication (get-in @players [selected-player-id :race])
(defn enemy? [player-id selected-player-id]
  (and (get-in @players [selected-player-id :race])
       (not= (get-in @players [player-id :race])
             (get-in @players [selected-player-id :race]))))

(defn ally? [player-id selected-player-id]
  (= (get-in @players [player-id :race])
     (get-in @players [selected-player-id :race])))

(defn square [n]
  (Math/pow n 2))

(defn inside-circle?
  "Clojure formula of (x - center_x)² + (y - center_y)² < radius²"
  [x z center-x center-z radius]
  (< (+ (square (- x center-x)) (square (- z center-z))) (square radius)))

(defn- cancel-all-tasks-of-player [player-id]
  (when-let [player (get @players player-id)]
    (doseq [t (->> player :effects vals (keep :task))]
      (tea/cancel! t))))

(defn not-same-player? [player-id enemy-id]
  (when-let [player (get @players player-id)]
    (when-let [enemy (get @players enemy-id)]
      (let [{:keys [token]} player
            enemy-token (:token enemy)]
        (not (= enemy-token token))))))

(defn process-if-enemy-died [player-id enemy-id health-after-damage current-players]
  (when (= 0 health-after-damage)
    (add-killing player-id enemy-id)
    (cancel-all-tasks-of-player enemy-id)
    (swap! players assoc-in [enemy-id :last-time :died] (now))
    (add-effect :die enemy-id)
    (send! enemy-id :died true)
    (if-let [party-id (get-in current-players [player-id :party-id])]
      (let [member-ids (find-player-ids-by-party-id current-players party-id)
            party-size (count member-ids)
            bp (battle-points-by-party-size party-size)]
        (doseq [id member-ids
                :when (not-same-player? id enemy-id)]
          (let [_ (swap! players update-in [id :bp] (fnil + 0) bp)
                total-bp (get-in @players [player-id :bp])]
            (send! id :earned-bp {:bp bp
                                  :total total-bp})
            (redis/update-bp players id total-bp))))
      (when (not-same-player? player-id enemy-id)
        (let [bp (battle-points-by-party-size 1)
              _ (swap! players update-in [player-id :bp] (fnil + 0) bp)
              total-bp (get-in @players [player-id :bp])]
          (send! player-id :earned-bp {:bp bp
                                       :total total-bp})
          (redis/update-bp players player-id total-bp))))))

(defn ping-too-high? [ping]
  (> ping 5000))

(defn has-battle-fury? [players id]
  (get-in players [id :effects :battle-fury :result]))

(defn increase-damage-if-has-battle-fury [damage players id]
  (if (has-battle-fury? players id)
    (int (* damage 1.1))
    damage))

(defn get-attack-power [player]
  (let [{:keys [weapon]} (:equip player)
        {:keys [item level]} weapon
        ap (get common.skills/level->attack-power-table (player :level))]
    (if weapon
      (* (+ ap (get-in common.item/items [item :levels level :ap] 0))
         (+ 1 (/ level 100)))
      ap)))

(defn attack-to-npc [{:keys [id
                             attack-power
                             selected-player-id
                             current-world
                             effect
                             skill
                             player
                             ping
                             validate-attack-skill-fn
                             slow-down?
                             priest-skill?
                             break-defense?
                             dont-use-mana?]}]
  (when-let [npc (get @npc/npcs selected-player-id)]
    (let [player-world-state (get current-world id)]
      (if-let [err (when validate-attack-skill-fn
                     (validate-attack-skill-fn {:id id
                                                :ping ping
                                                :selected-player-id selected-player-id
                                                :player-world-state player-world-state
                                                :npc-world-state npc
                                                :skill skill
                                                :player player
                                                :priest-skill? priest-skill?}))]
        err
        (let [_ (update-last-combat-time id)
              damage (npc/make-player-attack! {:skill skill
                                               :attack-power attack-power
                                               :player player
                                               :npc npc
                                               :slow-down? slow-down?
                                               :break-defense? break-defense?})]
          (when-not dont-use-mana?
            (swap! world update-in [id :mana] - (get-required-mana skill)))
          (some-> effect (add-effect-to-npc selected-player-id))
          (make-asas-appear-if-hidden id)
          (swap! players assoc-in [id :last-time :skill skill] (now))
          {:skill skill
           :damage damage
           :npc? true
           :selected-player-id selected-player-id})))))

(defn below-level-10? [player]
  (< (:level player) 10))

(defn enemy-below-level-10? [enemy-id]
  (let [enemy (get @players enemy-id)]
    (and enemy (below-level-10? enemy))))

(defn- validate-attack-r [{:keys [id
                                  ping
                                  selected-player-id
                                  player-world-state
                                  other-player-world-state
                                  npc-world-state
                                  skill
                                  player]}]
  (cond
    (ping-too-high? ping) ping-high
    (not (alive? player-world-state)) skill-failed
    (and other-player-world-state
         (not (alive? other-player-world-state))) skill-failed
    (and npc-world-state
         (not (alive? npc-world-state))) skill-failed
    (and (nil? npc-world-state)
         (not (enemy? id selected-player-id))) skill-failed
    (not (enough-mana? skill player-world-state)) not-enough-mana
    (and other-player-world-state
         (below-level-10? player)) pvp-locked
    (and other-player-world-state
         (enemy-below-level-10? selected-player-id)) enemy-low-level
    (not (satisfies-level? skill player)) skill-failed
    (not (cooldown-finished? skill player)) skill-failed
    (and other-player-world-state
         (not (close-for-attack? player-world-state other-player-world-state))) too-far
    (and npc-world-state
         (not (close-for-attack-to-npc? player-world-state npc-world-state))) too-far))

(defmethod apply-skill "attackR" [{:keys [id ping] {:keys [skill selected-player-id npc?]} :data}]
  (let [players* @players]
    (when-let [player (get players* id)]
      (if npc?
        (attack-to-npc {:id id
                        :ping ping
                        :selected-player-id selected-player-id
                        :current-world @world
                        :effect :attack-r
                        :skill skill
                        :player player
                        :attack-power (get-attack-power player)
                        :validate-attack-skill-fn validate-attack-r})
        (when (get players* selected-player-id)
          (let [w @world
                player-world-state (get w id)
                other-player-world-state (get w selected-player-id)]
            (if-let [err (validate-attack-r {:id id
                                             :ping ping
                                             :selected-player-id selected-player-id
                                             :player-world-state player-world-state
                                             :other-player-world-state other-player-world-state
                                             :skill skill
                                             :player player})]
              err
              (let [_ (update-last-combat-time id selected-player-id)
                    required-mana (get-required-mana skill)
                    attack-power (get-attack-power player)
                    damage ((-> common.skills/skills (get skill) :damage-fn)
                            (has-defense? selected-player-id)
                            (has-break-defense? selected-player-id)
                            attack-power)
                    damage (increase-damage-if-has-battle-fury damage players* id)
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
                (process-if-enemy-died id selected-player-id health-after-damage players*)
                (send! selected-player-id :got-attack-r-damage {:damage damage
                                                                :player-id id})
                {:skill skill
                 :damage damage
                 :selected-player-id selected-player-id}))))))))

;; TODO scheduler'da ilerideki bir taski iptal etmenin yolunu bul, ornegin adam posion yedi ama posion gecene kadar cure aldi gibi...
;; TODO ADJUST COOLDOWN FOR ASAS CLASS
(defmethod apply-skill "fleetFoot" [{:keys [id ping] {:keys [skill]} :data}]
  (when-let [player (get @players id)]
    (when-let [world-state (get @world id)]
      (cond
        (ping-too-high? ping) ping-high
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
        (ping-too-high? ping) ping-high
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
        (ping-too-high? ping) ping-high
        (not (alive? world-state)) skill-failed
        (not (cooldown-finished? skill player)) skill-failed
        (not (cooldown-finished? "hpPotion" player)) skill-failed
        :else (let [total-mana (get player :mana)]
                (swap! world update-in [id :mana] use-potion (-> common.skills/skills (get skill) :mp) total-mana)
                (swap! players assoc-in [id :last-time :skill skill] (now))
                (add-effect :mp-potion id)
                {:skill skill})))))

(defmethod apply-skill "baseDamage" [{:keys [id current-world data]}]
  (update-last-combat-time id)
  (let [damage (common.skills/rand-between 450 550)
        player-world-state (get current-world id)
        health-after-damage (- (:health player-world-state) damage)
        health-after-damage (Math/max ^long health-after-damage 0)]
    (swap! world assoc-in [id :health] health-after-damage)
    (add-effect :attack-base id)
    (when (= 0 health-after-damage)
      (send! id :died true)
      (cancel-all-tasks-of-player id)
      (swap! players assoc-in [id :last-time :died] (now)))
    {:skill (:skill data)
     :damage damage}))

(defmethod apply-skill "npcDamage" [{:keys [id current-world current-players data]}]
  (update-last-combat-time id)
  (when-let [player (get current-players id)]
    (let [damage (:damage data)
          player-world-state (get current-world id)
          health-after-damage (- (:health player-world-state) damage)
          health-after-damage (Math/max ^long health-after-damage 0)
          exp (player :exp)
          required-exp (player :required-exp)
          exp-to-lose (Math/round (* required-exp 0.05))
          exp (Math/max ^long (- exp exp-to-lose) 0)]
      (swap! world assoc-in [id :health] health-after-damage)
      (add-effect :attack-base id)
      (when (= 0 health-after-damage)
        (send! id :died true)
        (cancel-all-tasks-of-player id)
        (add-effect :die id)
        (swap! players (fn [players]
                         (-> players
                             (assoc-in [id :last-time :died] (now))
                             (assoc-in [id :exp] exp))))

        (redis/update-exp (:token player) (:class player) exp))
      (cond-> {:skill (:skill data)
               :npc-id (:npc-id data)
               :damage damage}
        (= 0 health-after-damage) (assoc :lost-exp exp-to-lose
                                         :exp exp)))))

(defmethod apply-skill "restoreHpMp" [{:keys [current-players current-world]}]
  (let [now (now)
        players-to-restore-hp-&-mp (->> current-players
                                        (filter
                                          (fn [[_ p]]
                                            (and (>= (- now (get-in p [:last-time :combat] 0)) 10000)
                                                 (> (get-in current-world [(:id p) :health] 0) 0))))
                                        (map
                                          (fn [[_ p]]
                                            {:id (:id p)
                                             :mp (int (* 0.2 (:mana p)))
                                             :hp (int (* 0.2 (:health p)))})))]
    (when (seq players-to-restore-hp-&-mp)
      (swap! world (fn [world]
                     (reduce
                       (fn [world {:keys [id hp mp]}]
                         (let [total-hp (get-in current-players [id :health])
                               total-mp (get-in current-players [id :mana])
                               hp (+ hp (get-in world [id :health]))
                               mp (+ mp (get-in world [id :mana]))
                               hp (Math/min hp total-hp)
                               mp (Math/min mp total-mp)]
                           (-> world
                               (assoc-in [id :health] hp)
                               (assoc-in [id :mana] mp))))
                       world
                       players-to-restore-hp-&-mp)))))
  nil)

(def re-spawn-failed {:error :re-spawn-failed})

(defn re-spawn-duration-finished? [player]
  (> (- (now) (get-in player [:last-time :died])) common.skills/re-spawn-duration-in-milli-secs))

(reg-pro
  :re-spawn
  (fn [{:keys [id data current-players current-world]}]
    (update-last-activity-time id)
    (when-let [player (get current-players id)]
      (when-let [world-state (get current-world id)]
        (cond
          (alive? world-state) re-spawn-failed
          (not (re-spawn-duration-finished? player)) re-spawn-failed
          :else (let [_ (cancel-all-tasks-of-player id)
                      {:keys [race class data]} player
                      new-pos (get-pos-for-player race class data)
                      {:keys [px py pz]} world-state
                      new-pos (if (:commercial-break-rewarded data)
                                [px py pz]
                                new-pos)
                      [x y z] new-pos]
                  (when-not (:commercial-break-rewarded data)
                    (swap! players assoc-in [id :in-enemy-base?] false))
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
                   :mana (:mana player)}))))))

(reg-pro
  :trigger-base
  (fn [{:keys [id data]}]
    (swap! players assoc-in [id :in-enemy-base?] data)
    nil))

(def valid-drop-range 12)

(defn can-player-get-the-drop? [player-id current-players current-world npc]
  (let [player (and (get current-players player-id)
                    (get current-world player-id))]
    (and player (alive? player) (close-npc-distance? player npc valid-drop-range))))

(defn- process-drop [drop npc-type exp current-players player-id party-size]
  (let [player (get current-players player-id)
        class (player :class)
        level (player :level)
        current-exp (player :exp)
        required-exp (player :required-exp)
        coin-drop (:coin drop 0)
        coin (+ (:coin player 0) coin-drop)
        exp (npc/calculate-exp level exp)
        exp (if party-size
              (Math/round (/ exp (+ (double (/ party-size 10)) 1.2)))
              exp)
        level-up? (>= (+ current-exp exp) required-exp)
        new-exp (if level-up? 0 (+ current-exp exp))
        new-level (when level-up? (inc level))
        {:keys [health mana]} (when level-up? (get-in common.skills/level->health-mana-table [new-level class]))
        new-required-exp (when level-up? (get common.skills/level->exp-table new-level))
        token (player :token)
        attack-power (when level-up? (get common.skills/level->attack-power-table new-level))
        level-15-rewarded-coin 50000
        coin (if (= new-level common.skills/chick-destroyed-level)
               (+ coin level-15-rewarded-coin)
               coin)]
    (send! player-id :drop (cond-> {:drop drop
                                    :npc npc-type
                                    :npc-exp exp
                                    :exp new-exp}
                             level-up?
                             (assoc :level-up? true
                                    :level new-level
                                    :attack-power attack-power
                                    :required-exp new-required-exp
                                    :health health
                                    :mana mana)
                             (> coin-drop 0)
                             (assoc :coin coin-drop
                                    :total-coin coin)))
    (when (and level-up? (= new-level common.skills/chick-destroyed-level))
      (doseq [id (filter #(not= player-id %) (keys current-players))]
        (send! id :chick-destroyed {:player-id player-id})))
    (when level-up?
      (add-effect :level-up player-id))
    (if level-up?
      (do
        (swap! players (fn [players]
                         (-> players
                             (assoc-in [player-id :exp] new-exp)
                             (assoc-in [player-id :level] new-level)
                             (assoc-in [player-id :health] health)
                             (assoc-in [player-id :mana] mana)
                             (assoc-in [player-id :required-exp] new-required-exp)
                             (assoc-in [player-id :coin] coin))))
        (swap! world (fn [world]
                       (-> world
                           (assoc-in [player-id :health] health)
                           (assoc-in [player-id :mana] mana))))
        (redis/level-up token class {:level new-level
                                     :exp new-exp
                                     :coin coin})
        (when (= 2 new-level)
          (redis/update-last-played-race token (:race player))
          (redis/update-last-played-class token class)
          (redis/update-username token class (:username player))))
      (do
        (swap! players (fn [players]
                         (-> players
                             (assoc-in [player-id :exp] new-exp)
                             (assoc-in [player-id :coin] coin))))
        (redis/update-exp-&-coin token class new-exp coin)))))

(reg-pro
  :drop
  (fn [{:keys [data]}]
    (let [npc (:npc data)
          npc-type (:type npc)
          npc-exp (:exp npc)
          drop (npc.drop/get-drops (:drop npc))
          {:keys [party-id player-id]} (:top-damager data)
          current-players @players
          current-world @world]
      (when party-id
        (let [player-ids (find-player-ids-by-party-id current-players party-id)
              party-size (count player-ids)]
          (doseq [player-id player-ids]
            (when (can-player-get-the-drop? player-id current-players current-world npc)
              (process-drop drop npc-type npc-exp current-players player-id party-size)))))
      (when player-id
        (when (can-player-get-the-drop? player-id current-players current-world npc)
          (process-drop drop npc-type npc-exp current-players player-id nil)))
      nil)))

(comment
  (clojure.pprint/pprint @players)
  (clojure.pprint/pprint @world)
  ;;close all connections, fetch :socket attribute and call s/close!
  (doseq [id (keys @players)]
    (s/close! (:socket (get @players id))))

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
                       (keys players)))))


  (clojure.pprint/pprint @players)
  (clojure.pprint/pprint @world)

  (swap! world (fn [world]
                 (reduce (fn [world id]
                           (assoc-in world [id :mana] 1))
                         world
                         (keys @players))))

  (reset-states)
  ;; set everyones health to 1600 in world atom
  ;; move above to a function using defn
  (swap! world (fn [world]
                 (reduce (fn [world id]
                           (-> world
                               (assoc-in [id :health] 100)
                               (assoc-in [id :mana] 5)))
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

  (mount/start #'snapshot-sender)
  (mount/stop #'snapshot-sender)
  (mount/start)
  )

(reg-pro
  :skill
  (fn [{:keys [id] :as opts}]
    (try
      (update-last-activity-time id)
      (apply-skill opts)
      (catch Throwable e
        (log/error e
                   "Something went wrong when applying skill."
                   (pr-str (select-keys opts [:id :data :ping]))
                   (get @players (:id opts))
                   (get @world (:id opts)))
        (sentry/send-event {:message (pr-str {:data (select-keys opts [:id :data :ping])
                                              :player-data (get @players (:id opts))
                                              :world-state (get @world (:id opts))})
                            :throwable e})
        skill-failed))))

(defmulti process-party-request (fn [{:keys [data]}]
                                  (:type data)))

(defn leader? [player]
  (:party-leader? player))

(defn already-in-the-party? [player other-player]
  (and (:party-id other-player)
       (= (:party-id player) (:party-id other-player))))

;; TODO bug, human exit yapinca orc'ta partyden cikti diyor
(defn remove-from-party [{:keys [players* id selected-player-id exit?]}]
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
                             (reduce (fn [players [id _]]
                                       (-> players
                                           (assoc-in [id :party-id] nil)
                                           (assoc-in [id :party-leader?] false)))
                                     players
                                     players))))
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

(reg-pro
  :party
  (fn [{:keys [id] :as opts}]
    (try
      (update-last-activity-time id)
      (process-party-request opts)
      (catch Throwable e
        (log/error e
                   "Something went wrong when applying party request."
                   (pr-str (select-keys opts [:id :data :ping]))
                   (get @players (:id opts)))
        (sentry/send-event {:message (pr-str {:data (select-keys opts [:id :data :ping])
                                              :player-data (get @players (:id opts))})
                            :throwable e})
        party-request-failed))))

(defn reset-states []
  (reset! world {})
  (reset! players {})
  (reset! utils/id-generator 0))

(defn ws-handler
  [req]
  (-> (http/websocket-connection req)
      (d/chain
        (fn [socket]
          (let [now* (now)
                player-id (swap! utils/id-generator inc)
                token (-> req :params :token)]
            (alter-meta! socket assoc :id player-id)
            (swap! players (fn [players]
                             (-> players
                                 (assoc-in [player-id :id] player-id)
                                 (assoc-in [player-id :socket] socket)
                                 (assoc-in [player-id :time] now*)
                                 (assoc-in [player-id :token] token))))
            (s/on-closed socket
                         (fn []
                           (let [username (get-in @players [player-id :username])]
                             (cancel-all-tasks-of-player player-id)
                             (remove-from-party {:id player-id
                                                 :players* @players
                                                 :exit? true})
                             (swap! players dissoc player-id)
                             (swap! world dissoc player-id)
                             (future
                               (Thread/sleep 1000)
                               (swap! world dissoc player-id)
                               (notify-players-for-exit player-id username))))))
          (s/consume
            (fn [payload]
              (try
                (let [id (-> socket meta :id)
                      payload (msg/unpack payload)]
                  (dispatch (:pro payload) {:id id
                                            :data (:data payload)
                                            :ping (get-in @players [id :ping] 0)
                                            :current-players @players
                                            :current-world @world
                                            :req req
                                            :socket socket
                                            :send-fn (fn [socket {:keys [id result]}]
                                                       (when result
                                                         (s/put! socket (msg/pack (hash-map id result)))))}))
                (catch Exception e
                  (log/error e)
                  (sentry/send-event {:message (pr-str {:message (.getMessage e)
                                                        :payload {:data 1}})
                                      :throwable e}))))
            socket))))
  ;; Routing lib expects some sort of HTTP response, so just give it `nil`
  nil)

(defn- player-info [req]
  {:status 200
   :body (some-> req :params :token redis/get)})

(defn- stats [_]
  (let [players @players
        orcs (count (get-orcs players))
        humans (count (get-humans players))]
    {:status 200
     :body {:number-of-players (+ orcs humans)
            :orcs orcs
            :humans humans
            :max-number-of-same-race-players max-number-of-same-race-players
            :max-number-of-players max-number-of-players}}))

(defn- get-servers-list [_]
  {:status 200
   :body {"EU-1" {:ws-url "wss://enion-eu-1.fly.dev:443/ws"
                  :stats-url "https://enion-eu-1.fly.dev/stats"}
          ;; "EU-2" {:ws-url "wss://enion-eu-2.fly.dev:443/ws"
          ;;        :stats-url "https://enion-eu-2.fly.dev/stats"}
          ;; "EU-3" {:ws-url "wss://enion-eu-3.fly.dev:443/ws"
          ;;        :stats-url "https://enion-eu-3.fly.dev/stats"}
          ;; "BR-1" {:ws-url "wss://enion-br-1.fly.dev:443/ws"
          ;;        :stats-url "https://enion-br-1.fly.dev/stats"}
          ;; "BR-2" {:ws-url "wss://enion-br-2.fly.dev:443/ws"
          ;;        :stats-url "https://enion-br-2.fly.dev/stats"}
          }})

(defn home-routes
  []
  [""
   {:middleware [middleware/wrap-formats]}
   ["/" {:get home-page}]
   ["/player_info" {:post player-info}]
   ["/stats" {:post stats}]
   ["/servers" {:get get-servers-list}]
   ["/ws" {:get ws-handler}]])

(defstate register-procedures
  :start (easync/start-procedures))

(defstate ^{:on-reload :noop} snapshot-sender
  :start (send-world-snapshots)
  :stop (shutdown snapshot-sender))

(defstate ^{:on-reload :noop} afk-player-checker
  :start (check-afk-players)
  :stop (shutdown afk-player-checker))

(defstate ^{:on-reload :noop} enemies-entered-base-checker
  :start (check-enemies-entered-base)
  :stop (shutdown enemies-entered-base-checker))

(defstate ^{:on-reload :noop} leftover-player-state-checker
  :start (check-leftover-player-states)
  :stop (shutdown leftover-player-state-checker))

(defstate ^{:on-reload :noop} restore-hp-&-mp
  :start (restore-hp-&-mp-for-players-out-of-combat)
  :stop (shutdown restore-hp-&-mp))

(defstate ^{:on-reload :noop} teatime-pool
  :start (tea/start!)
  :stop (tea/stop!))

(defstate ^{:on-reload :noop} init-sentry
  :start (sentry/init! "https://9080b8a52af24bdb9c637555f1a36a1b@o4504713579724800.ingest.sentry.io/4504731298693120"
                       {:traces-sample-rate 1.0}))

(defstate ^{:on-reload :noop} init-npcs
  :start (npc/init-npcs)
  :stop (npc/clear-npcs))

