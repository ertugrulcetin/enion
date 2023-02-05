(ns enion-cljs.scene.network
  (:require
    [applied-science.js-interop :as j]
    [enion-cljs.common :refer [fire on dlog]]
    [enion-cljs.scene.pc :as pc]
    [enion-cljs.scene.skills.effects :as effects]
    [enion-cljs.scene.states :as st]
    [msgpack-cljs.core :as msg]))

(defonce socket (atom nil))
(defonce open? (atom false))
(defonce world nil)
(defonce current-player-id nil)

(defonce send-state-interval-id (atom nil))

(def sending-states-to-server-tick-rate (/ 1000 30))

(defn connect [{:keys [url on-open on-message on-close on-error]}]
  (reset! socket (js/WebSocket. url))
  (j/assoc! @socket :binaryType "arraybuffer")
  (some->> on-open (j/call @socket :addEventListener "open"))
  (some->> on-message (j/call @socket :addEventListener "message"))
  (some->> on-close (j/call @socket :addEventListener "close"))
  (some->> on-error (j/call @socket :addEventListener "error")))

(defn dispatch-pro
  ([pro]
   (dispatch-pro pro nil))
  ([pro data]
   (if @open?
     (j/call @socket :send (msg/pack {:pro pro
                                      :data data
                                      :timestamp (js/Date.now)}))
     (js/console.error "Connection closed, can't send the payload!"))))

(defmulti dispatch-pro-response ffirst)

(defmethod dispatch-pro-response :init [params]
  (fire :init (:init params))
  (set! current-player-id (-> params :init :id)))

(defmethod dispatch-pro-response :player-join [params]
  (dlog "new join" (:player-join params))
  (fire :create-players [(:player-join params)]))

(defmethod dispatch-pro-response :player-exit [params]
  (dlog "player left")
  (-> (:player-exit params) st/destroy-player))

(defn- process-world-snapshot-for-player [state]
  (let [health (:health state)
        mana (:mana state)]
    (st/set-health health)
    (st/set-mana mana)
    (when (= 0 health)
      (pc/set-anim-int (st/get-model-entity) "health" 0))))

(let [skills-effects-before-response #{"heal" "cure" "breakDefense"}]
  (defn- process-world-snapshot [world]
    (doseq [s world
            :let [id (str (first s))
                  new-state (second s)
                  other-player (st/get-other-player id)
                  entity (j/get other-player :entity)
                  health (:health new-state)
                  new-anim-state (:st new-state)]
            :when (and entity (or (st/alive? id) (> health 0)))]
      (st/set-health id health)
      (if (= health 0)
        (do
          (pc/set-anim-int (st/get-model-entity id) "health" 0)
          (st/disable-player-collision id))
        (do
          ;; TODO remove 'constantly' for prod
          (when-let [tw (j/get-in st/other-players [id :tween :interpolation])]
            (j/call (tw) :stop))
          ;; TODO bu checkten dolayi karakter havada basliyor
          ;; TODO also do not run (st/move-player) for players far away - LOD optimization
          (when (or (not= "idle" new-anim-state)
                    ;; TODO buradan dolayi yeni gelen oyuncular karakterleri eski poziyonda goruyor sanirim surekli render etmek lazim
                    (not (some-> id st/get-model-entity pc/get-anim-state (= "idle"))))
            (let [pos (pc/get-pos entity)
                  x (j/get pos :x)
                  y (j/get pos :y)
                  z (j/get pos :z)
                  initial-pos (j/get-in st/other-players [id :tween :initial-pos])
                  _ (j/assoc! initial-pos :x x :y y :z z)
                  last-pos (j/get-in st/other-players [id :tween :last-pos])
                  _ (j/assoc! last-pos :x (:px new-state) :y (:py new-state) :z (:pz new-state))
                  tween-interpolation (-> (j/call entity :tween initial-pos)
                                          (j/call :to last-pos 0.05 pc/linear))
                  _ (j/call tween-interpolation :on "update"
                            (fn []
                              (let [x (j/get initial-pos :x)
                                    y (j/get initial-pos :y)
                                    z (j/get initial-pos :z)]
                                (st/move-player other-player entity x y z))))]
              (j/call tween-interpolation :start)
              (j/assoc-in! st/other-players [id :tween :interpolation] (constantly tween-interpolation))))
          (st/rotate-player id (:ex new-state) (:ey new-state) (:ez new-state))
          (st/set-anim-state id new-anim-state)
          (when (and (skills-effects-before-response new-anim-state)
                     (not= (j/get-in st/other-players [id :prev-state]) new-anim-state))
            (case new-anim-state
              "heal" (effects/apply-effect-heal-particles other-player)
              "cure" (effects/apply-effect-cure-particles other-player)
              "breakDefense" (effects/apply-effect-defense-break-particles other-player)
              (effects/apply-effect-heal-particles other-player))
            (j/assoc-in! st/other-players [id :prev-state] new-anim-state)))))))

(defn- process-effects [effects]
  (doseq [[e ids] effects
          id ids
          :when (not= id current-player-id)
          :let [enemy-state (st/get-other-player id)]]
    (case e
      :attack-r (effects/apply-effect-attack-r enemy-state)
      :attack-dagger (effects/apply-effect-attack-dagger enemy-state)
      :attack-one-hand (effects/apply-effect-attack-one-hand enemy-state)
      :attack-slow-down (effects/apply-effect-attack-slow-down enemy-state)
      :hp-potion (effects/apply-effect-hp-potion enemy-state)
      :mp-potion (effects/apply-effect-mp-potion enemy-state)
      :fleet-foot (effects/apply-effect-fleet-foot enemy-state)
      :heal (effects/add-player-id-to-healed-ids id)
      :cure (effects/apply-effect-got-cure enemy-state)
      :hide (j/call-in enemy-state [:skills :hide])
      :appear (j/call-in enemy-state [:skills :appear])
      :else (js/console.error "Unknown effect: " e))))

(defmethod dispatch-pro-response :world-snapshot [params]
  (let [ws (:world-snapshot params)
        effects (:effects ws)
        ws (dissoc ws :effects)]
    (set! world ws)
    (process-world-snapshot-for-player (get ws current-player-id))
    ;; TODO when tab is not focused, send that data to server and server makes the state idle for this player to other players
    (process-world-snapshot (dissoc ws current-player-id))
    (process-effects effects)))

(defn send-states-to-server []
  (if-let [id @send-state-interval-id]
    (js/clearInterval id)
    (reset! send-state-interval-id (js/setInterval
                                     #(some->> (st/get-state) (dispatch-pro :set-state))
                                     sending-states-to-server-tick-rate))))

(defn cancel-sending-states-to-server []
  (some-> @send-state-interval-id js/clearInterval)
  (reset! send-state-interval-id nil))

(defmethod dispatch-pro-response :connect-to-world-state [params]
  (when (:connect-to-world-state params)
    (send-states-to-server)
    (dispatch-pro :request-all-players)))

(defmethod dispatch-pro-response :request-all-players [params]
  (let [players (:request-all-players params)
        current-players-ids (set (cons current-player-id (seq (js/Object.keys st/other-players))))
        players (remove #(or (current-players-ids (:id %)) (nil? (:id %))) players)]
    (when (seq players)
      (fire :create-players players))))

(on :start-ws
    (fn []
      (connect {:url "ws://localhost:3000/ws"
                :on-message (fn [event]
                              (dispatch-pro-response (msg/unpack (j/get event :data))))
                :on-open (fn []
                           (println "WS connection established.")
                           (reset! open? true)
                           (dispatch-pro :init {:username "NeaTBuSTeR"
                                                :race "orc"
                                                :class "asas"}))
                :on-close (fn []
                            (println "WS connection closed.")
                            (reset! open? false))
                :on-error (fn []
                            (println "WS error occurred!")
                            (reset! open? false))})))

(on :connect-to-world-state #(dispatch-pro :connect-to-world-state))

(comment
  (fire :start-ws)
  (dispatch-pro :init {:username "NeaTBuSTeR"
                       :race "orc"
                       :class "warrior"})


  (send-states-to-server)
  (cancel-sending-states-to-server)


  (connect {:url "ws://localhost:3000/ws"
            :on-message (fn [event]
                          (dispatch-pro-response (msg/unpack (j/get event :data))))
            :on-open (fn []
                       (println "WS connection established.")
                       (reset! open? true))
            :on-close (fn []
                        (println "WS connection closed.")
                        (reset! open? false))
            :on-error (fn []
                        (println "WS error occurred!")
                        (reset! open? false))})
  )
