(ns enion-cljs.scene.network
  (:require
    [applied-science.js-interop :as j]
    [enion-cljs.common :refer [fire on]]
    [enion-cljs.scene.entities.player :as entity.player]
    [enion-cljs.scene.pc :as pc]
    [enion-cljs.scene.states :as st]
    [msgpack-cljs.core :as msg]))

(defonce socket (atom nil))
(defonce open? (atom false))
(defonce world nil)
(defonce current-player-id nil)

(defonce send-state-interval-id (atom nil))

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
  (println "new join!")
  (-> (:player-join params) entity.player/create-player st/add-player))

(defmethod dispatch-pro-response :player-exit [params]
  (println "player left")
  (-> (:player-exit params) st/destroy-player))

(defmethod dispatch-pro-response :spawn [params]
  (entity.player/spawn (:spawn params)))

(defn- process-world-snapshot [world]
  (doseq [s (dissoc world current-player-id)
          :let [id (first s)
                s (second s)
                px (:px s)
                py (:py s)
                pz (:pz s)
                ex (:ex s)
                ey (:ey s)
                ez (:ez s)
                st (:st s)
                entity (st/get-other-player-entity id)]
          :when entity]
    (when-let [tw (j/get-in st/other-players [id :tween :interpolation])]
      (j/call tw :stop))
    (let [pos (pc/get-pos entity)
          x (j/get pos :x)
          y (j/get pos :y)
          z (j/get pos :z)
          initial-pos (j/get-in st/other-players [id :tween :initial-pos])
          _ (j/assoc! initial-pos :x x :y y :z z)
          last-pos (j/get-in st/other-players [id :tween :last-pos])
          _ (j/assoc! last-pos :x px :y py :z pz)
          tween-interpolation (-> (j/call entity :tween initial-pos)
                                  (j/call :to last-pos 0.05 pc/linear))
          _ (j/call tween-interpolation :on "update"
                    (fn []
                      (let [x (j/get initial-pos :x)
                            y (j/get initial-pos :y)
                            z (j/get initial-pos :z)]
                        (st/move-player id x y z))))]
      (j/call tween-interpolation :start)
      (j/assoc-in! st/other-players [id :tween :interpolation] tween-interpolation)
      (st/rotate-player id ex ey ez)
      (st/set-anim-state id st)
      nil)))

(defmethod dispatch-pro-response :world-snapshot [params]
  (let [ws (:world-snapshot params)]
    (set! world ws)
    (process-world-snapshot ws)))

(def sending-states-to-server-tick-rate (/ 1000 30))

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
    (send-states-to-server)))

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
                                                :class "warrior"}))
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
