(ns enion-backend.routes.home
  (:require
    [aleph.http :as http]
    [clojure.edn :as edn]
    [clojure.java.io :as io]
    [clojure.set :as set]
    [clojure.string :as str]
    [clojure.tools.logging :as log]
    [common.enion.skills :as common.skills]
    [enion-backend.async :as easync :refer [dispatch reg-pro]]
    [enion-backend.layout :as layout]
    [enion-backend.middleware :as middleware]
    [enion-backend.teatime :as tea]
    [enion-backend.utils :as utils :refer [dev?]]
    [manifold.deferred :as d]
    [manifold.stream :as s]
    [mount.core :as mount :refer [defstate]]
    [msgpack.clojure-extensions]
    [msgpack.core :as msg]
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

(defonce id-generator (atom 0))
(defonce party-id-generator (atom 0))

(def max-number-of-party-members 5)

(defonce players (atom {}))
(defonce world (atom {}))

(defonce effects-stream (s/stream))
(defonce killings-stream (s/stream))

(defn now []
  (.toEpochMilli (Instant/now)))

(defn add-effect [effect data]
  (s/put! effects-stream {:effect effect
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

(defn- send-world-snapshots* []
  (let [effects (->> effects-stream
                     (take-while-stream comp-not-nil)
                     create-effects->player-ids-mapping)
        kills (take-while-stream comp-not-nil killings-stream)
        w @world
        w (if (empty? effects) w (assoc w :effects effects))
        w (if (empty? kills) w (assoc w :kills kills))
        current-players @players
        party-id->players (get-players-with-defense current-players)]
    (doseq [player-id (keys w)
            :let [party-id (get-in current-players [player-id :party-id])
                  w (if-let [members-ids (seq (get party-id->players party-id))]
                      (assoc w :break-defense (set members-ids))
                      w)]]
      (send! player-id :world-snapshot w))))

(defn- create-single-thread-executor [millisecs f]
  (doto (Executors/newSingleThreadScheduledExecutor)
    (.scheduleAtFixedRate f 0 millisecs TimeUnit/MILLISECONDS)))

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
    (create-single-thread-executor 1000 (fn [] (check-afk-players*)))))

(defn dispatch-in [pro-name {:keys [id data]}]
  (let [current-players @players]
    (when-let [socket (get-in current-players [id :socket])]
      (try
        (dispatch pro-name {:id id
                            :ping 0
                            :data data
                            :req {}
                            :current-players current-players
                            :current-world @world
                            :socket socket
                            :send-fn (fn [socket {:keys [id result]}]
                                       (when result
                                         (s/put! socket (msg/pack (hash-map id result)))))})
        (catch Exception e
          (log/error e))))))

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
                             :data {:skill "baseDamage"}})))
    (catch Exception e
      (println "Couldn't check enemies entered base")
      (log/error e))))

(defn- check-enemies-entered-base []
  (create-single-thread-executor 1000 (fn [] (check-enemies-entered-base*))))

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

(defstate ^{:on-reload :noop} afk-player-checker
  :start (check-afk-players)
  :stop (shutdown afk-player-checker))

(defstate ^{:on-reload :noop} enemies-entered-base-checker
  :start (check-enemies-entered-base)
  :stop (shutdown enemies-entered-base-checker))

(defstate ^{:on-reload :noop} teatime-pool
  :start (tea/start!)
  :stop (tea/stop!))

(defstate register-procedures
  :start (easync/start-procedures))

(defstate ^{:on-reload :noop} init-sentry
  :start (sentry/init! "https://9080b8a52af24bdb9c637555f1a36a1b@o4504713579724800.ingest.sentry.io/4504731298693120"
                       {:traces-sample-rate 1.0}))

(def selected-keys-of-set-state [:px :py :pz :ex :ey :ez :st])

(defn- update-last-activity-time [id]
  (when (get @players id)
    (swap! players assoc-in [id :last-time :activity] (now))))

(reg-pro
  :set-state
  (fn [{:keys [id data]}]
    (swap! world update id merge (select-keys data selected-keys-of-set-state))
    (update-last-activity-time id)
    nil))

(reg-pro
  :ping
  (fn [{:keys [id ping]}]
    (swap! players assoc-in [id :ping] ping)
    {:ping ping
     :online (count @world)}))

(defn get-orcs [players]
  (filter (fn [[_ p]] (= "orc" (:race p))) players))

(defn get-humans [players]
  (filter (fn [[_ p]] (= "human" (:race p))) players))

(reg-pro
  :get-server-stats
  (fn [_]
    (let [players @players
          orcs (count (get-orcs players))
          humans (count (get-humans players))]
      {:number-of-players (+ orcs humans)
       :orcs orcs
       :humans humans
       :max-number-of-same-race-players max-number-of-same-race-players
       :max-number-of-players max-number-of-players})))

(reg-pro
  :get-score-board
  (fn [{:keys [id]}]
    (update-last-activity-time id)
    (let [players @players
          orcs (map (fn [[_ p]] (assoc p :bp (or (:bp p) 0))) (get-orcs players))
          humans (map (fn [[_ p]] (assoc p :bp (or (:bp p) 0))) (get-humans players))]
      (when-let [players* (seq (concat orcs humans))]
        (map #(select-keys % [:id :race :class :bp]) players*)))))

(defn find-player-ids-by-race [players race]
  (->> players
       (filter
         (fn [[_ v]]
           (= race (:race v))))
       (map first)))

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
    (send! other-player-id :player-join attrs)))

(defn notify-players-for-exit [id]
  (doseq [other-player-id (filter #(not= id %) (keys @world))]
    (println "Sending exit notification...")
    (send! other-player-id :player-exit id)))

(defn prob? [prob]
  (< (rand) prob))

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

(reg-pro
  :init
  (fn [{:keys [id current-players] {:keys [username race class]} :data}]
    (cond
      ;; TODO add ping check here, because users could wait others...
      (full?) {:error :server-full}
      (and (= race "human") (human-race-full?)) {:error :human-race-full}
      (and (= race "orc") (orc-race-full?)) {:error :orc-race-full}
      (and (not (str/blank? username))
           (not (common.skills/username? username))) {:error :invalid-username}
      (and (not (str/blank? username))
           (or ((get-usernames) (str/lower-case username))
               (= "system" (str/lower-case username)))) {:error :username-taken}
      :else (do
              (println "Player joining...")
              (let [orcs-count (count (get-orcs current-players))
                    humans-count (count (get-humans current-players))
                    race (or race (cond
                                    (human-race-full?) "orc"
                                    (orc-race-full?) "human"
                                    (< humans-count orcs-count) "human"
                                    :else "orc"))
                    class (or class (find-least-repetitive-class race current-players))
                    pos (if (= "orc" race)
                          (common.skills/random-pos-for-orc)
                          (common.skills/random-pos-for-human))
                    health (get-in common.skills/classes [class :health])
                    mana (get-in common.skills/classes [class :mana])
                    username (generate-username username race class current-players)
                    attrs {:id id
                           :username username
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

(def ping-high {:error :ping-high})
(def skill-failed {:error :skill-failed})
(def too-far {:error :too-far})
(def not-enough-mana {:error :not-enough-mana})
(def party-request-failed {:error :party-request-failed})

(defn alive? [state]
  (> (:health state) 0))

(defn enough-mana? [skill state]
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

(defn close-for-attack? [player-world-state other-player-world-state]
  (close-distance? player-world-state other-player-world-state (+ common.skills/close-attack-distance-threshold 0.4)))

(defn close-for-priest-skills? [player-world-state other-player-world-state]
  (close-distance? player-world-state other-player-world-state (+ common.skills/priest-skills-distance-threshold 0.5)))

(defn close-for-attack-single? [player-world-state other-player-world-state]
  (close-distance? player-world-state other-player-world-state (+ common.skills/attack-single-distance-threshold 0.5)))

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
    (add-effect :appear selected-player-id)
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

(defn process-if-enemy-died [player-id enemy-id health-after-damage players*]
  (when (= 0 health-after-damage)
    (add-killing player-id enemy-id)
    (cancel-all-tasks-of-player enemy-id)
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

(defn ping-too-high? [ping]
  (> ping 5000))

(defn has-battle-fury? [players id]
  (get-in players [id :effects :battle-fury :result]))

(defn increase-damage-if-has-battle-fury [damage players id]
  (if (has-battle-fury? players id)
    (int (* damage 1.1))
    damage))

(defmethod apply-skill "attackR" [{:keys [id ping] {:keys [skill selected-player-id]} :data}]
  (let [players* @players]
    (when-let [player (get players* id)]
      (when (get players* selected-player-id)
        (let [w @world
              player-world-state (get w id)
              other-player-world-state (get w selected-player-id)]
          (cond
            (ping-too-high? ping) ping-high
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
                     :selected-player-id selected-player-id})))))))

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
  (let [damage (common.skills/rand-between 350 400)
        player-world-state (get current-world id)
        health-after-damage (- (:health player-world-state) damage)
        health-after-damage (Math/max ^long health-after-damage 0)]
    (swap! world assoc-in [id :health] health-after-damage)
    (add-effect :attack-base id)
    (when (= 0 health-after-damage)
      (cancel-all-tasks-of-player id)
      (swap! players assoc-in [id :last-time :died] (now))
      (swap! players assoc-in [id :in-enemy-base?] false))
    {:skill (:skill data)
     :damage damage}))

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
                      new-pos (if (= "orc" (:race player))
                                (common.skills/random-pos-for-orc)
                                (common.skills/random-pos-for-human))
                      {:keys [px py pz]} world-state
                      new-pos (if (:commercial-break-rewarded data)
                                [px py pz]
                                new-pos)
                      [x y z] new-pos]
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

(comment

  (clojure.pprint/pprint @players)
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
                           (assoc-in world [id :mana] 350))
                   world
                   (keys @players))))

  (reset-states)
  ;; set everyones health to 1600 in world atom
  ;; move above to a function using defn
  (swap! world (fn [world]
                 (reduce (fn [world id]
                           (-> world
                             (assoc-in [id :health] 200000)
                             (assoc-in [id :mana] 200000)))
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
  (reset! id-generator 0))

(defn ws-handler
  [req]
  (-> (http/websocket-connection req)
      (d/chain
        (fn [socket]
          (let [now* (now)
                player-id (swap! id-generator inc)]
            (alter-meta! socket assoc :id player-id)
            (swap! players (fn [players]
                             (-> players
                                 (assoc-in [player-id :id] player-id)
                                 (assoc-in [player-id :socket] socket)
                                 (assoc-in [player-id :time] now*))))
            (s/on-closed socket
                         (fn []
                           (cancel-all-tasks-of-player player-id)
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

(defn- stats [req]
  (let [now (Instant/now)
        ping (- (.toEpochMilli now) (-> req :params :timestamp))
        players @players
        orcs (count (get-orcs players))
        humans (count (get-humans players))]
    {:status 200
     :body {:ping ping
            :number-of-players (+ orcs humans)
            :orcs orcs
            :humans humans
            :max-number-of-same-race-players max-number-of-same-race-players
            :max-number-of-players max-number-of-players}}))

(defn- get-servers-list [_]
  {:status 200
   :body {"EU-1" {:ws-url "wss://enion-eu-1.fly.dev:443/ws"
                  :stats-url "https://enion-eu-1.fly.dev/stats"}
          "EU-2" {:ws-url "wss://enion-eu-2.fly.dev:443/ws"
                  :stats-url "https://enion-eu-2.fly.dev/stats"}
          "EU-3" {:ws-url "wss://enion-eu-3.fly.dev:443/ws"
                  :stats-url "https://enion-eu-3.fly.dev/stats"}
          "BR-1" {:ws-url "wss://enion-br-1.fly.dev:443/ws"
                  :stats-url "https://enion-br-1.fly.dev/stats"}
          "BR-2" {:ws-url "wss://enion-br-2.fly.dev:443/ws"
                  :stats-url "https://enion-br-2.fly.dev/stats"}}})

(defn home-routes
  []
  [""
   {:middleware [middleware/wrap-formats]}
   ["/" {:get home-page}]
   ["/stats" {:post stats}]
   ["/servers" {:get get-servers-list}]
   ["/ws" {:get ws-handler}]])
