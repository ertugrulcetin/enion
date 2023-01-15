(ns enion-backend.routes.home
  (:require
    [aleph.http :as http]
    [clojure.java.io :as io]
    [enion-backend.layout :as layout]
    [enion-backend.middleware :as middleware]
    [manifold.deferred :as d]
    [manifold.stream :as s]
    [msgpack.clojure-extensions]
    [msgpack.core :as msg]
    [procedure.async :refer [dispatch reg-pro]]
    [ring.util.http-response :as response]
    [ring.util.response])
  (:import
    (java.time
      Instant)))

(def max-number-of-players 50)
(def max-number-of-same-race-players (/ max-number-of-players 2))

(defonce id-generator (atom 0))
(defonce world (atom {}))

(defn home-page
  [request]
  (layout/render request "home.html" {:docs (-> "docs/docs.md" io/resource slurp)}))

(comment
  (s/put! @my-soc (msg/pack {0 2}))

  (reset! run? false)
  (reset! run? true)

  (while @run?
    (s/put! @my-soc (msg/pack {:code (rand-int 10000)}))
    (Thread/sleep (/ 1000 20))))

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
  :init-player
  (fn [{:keys [data]}]
    (let [id (swap! id-generator inc)
          attrs {:id id
                 :username (:username data)
                 :race :orc
                 :class :warrior
                 :health 100
                 :mana 100
                 :pos (random-pos-for-orc)}]
      (swap! world assoc id attrs)
      attrs)))

(reg-pro
  :player-joined
  (fn [{:keys [data]}]
    {:id (rand-int 100000)
     :username "New Player"
     :race :orc
     :class :warrior
     :health 100
     :mana 100
     :pos (random-pos-for-orc)}))

(defn- ws-handler
  [req]
  (-> (http/websocket-connection req)
      (d/chain
        (fn [socket]
          ;; TODO register socket in here
          (s/consume
            (fn [payload]
              (let [now (Instant/now)
                    payload (msg/unpack payload)
                    ping (- (.toEpochMilli now) (:timestamp payload))]
                (dispatch (:pro payload) {:data (:data payload)
                                          :ping ping
                                          :req req
                                          :socket socket
                                          :send-fn (fn [socket {:keys [id result]}]
                                                     (when result
                                                       (s/put! socket (msg/pack (hash-map id result)))))})))
            socket)))))

(defn home-routes
  []
  [""
   {:middleware [middleware/wrap-csrf
                 middleware/wrap-formats]}
   ["/" {:get home-page}]
   ["/ws" {:get ws-handler}]])
