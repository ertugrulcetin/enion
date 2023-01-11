(ns enion-cljs.network.core
  (:require
    [applied-science.js-interop :as j]
    [enion-cljs.core :as core]
    [enion-cljs.scene.entities.player :as entity.player]
    [msgpack-cljs.core :as msg]))

(defonce socket (atom nil))
(defonce open? (atom false))

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

(defmethod dispatch-pro-response :init-player [params]
  (core/init-game (:init-player params)))

(defmethod dispatch-pro-response :spawn [params]
  (entity.player/spawn (:spawn params)))

(comment
  (dispatch-pro :init-player {:username "ertu"})
  (dispatch-pro :spawn)
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
