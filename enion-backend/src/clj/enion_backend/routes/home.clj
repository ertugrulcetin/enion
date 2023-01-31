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
  [player-id id result]
  ;; TODO check if socket closed or not!
  (when result
    (when-let [socket (get-in @players [player-id :socket])]
      (s/put! socket (msg/pack (hash-map id result))))))

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

(defmulti apply-skill (fn [{:keys [data]}]
                        (:skill data)))

;; TODO adjust cooldown for asas class
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
                true)))))

@world
(clojure.pprint/pprint @players)

(reg-pro
  :skill
  (fn [opts]
    (apply-skill opts)))

(defn- reset-states []
  (reset! world {})
  (reset! players {})
  (reset! id-generator 0))

(comment
  (reset-states)
  @world
  @players

  (mount/start)
  (mount/stop)
  )

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
