(ns enion-cljs.scene.entities.camera
  (:require
    [applied-science.js-interop :as j]
    [enion-cljs.scene.pc :as pc :refer [app]])
  (:require-macros
    [enion-cljs.scene.macros :refer [fnt]]))

;; TODO consider transient map
(defonce state (atom {:eulers (pc/vec3)
                      :mouse-edge-range 5
                      :mouse-over? false
                      :mouse-speed 1.4
                      :page-x nil
                      :right-click? false
                      :rotation-speed 50
                      :target-angle (pc/vec3)
                      :wheel-clicked? false
                      :wheel-x 0}))

(defonce entity nil)

(defn- mouse-move [e]
  (swap! state assoc :page-x (.-x e))
  (when (:right-click? @state)
    (set! (.-x (:eulers @state)) (- (.-x (:eulers @state)) (mod (/ (* (:mouse-speed @state) (.-dx e)) 60) 360)))
    (set! (.-y (:eulers @state)) (+ (.-y (:eulers @state)) (mod (/ (* (:mouse-speed @state) (.-dy e)) 60) 360)))))

(defn- mouse-wheel [e]
  (let [ray-end (:ray-end @state)
        ray-pos (pc/get-loc-pos ray-end)
        z (+ (.-z ray-pos) (* 0.1 (.-wheelDelta e)))
        z (cond
            (< z 2) 2
            (> z 3.5) 3.5
            :else z)]
    (pc/set-loc-pos ray-end (.-x ray-pos) (.-y ray-pos) z)))

(defn- mouse-down [e]
  (when (= (.-button e) (:MOUSEBUTTON_RIGHT pc/key->code))
    (swap! state assoc :right-click? true)))

(defn- mouse-up [e]
  (when (= (.-button e) (:MOUSEBUTTON_RIGHT pc/key->code))
    (swap! state assoc :right-click? false)))

(defn- mouse-out []
  (swap! state assoc
         :mouse-over? false
         :right-click? false))

(defn- mouse-over []
  (swap! state assoc :mouse-over? true))

(defn- init-fn [this]
  (swap! state assoc :ray-end (pc/find-by-name "ray_end"))
  (set! entity (j/get this :entity))
  (pc/disable-context-menu)
  (pc/mouse-on :EVENT_MOUSEMOVE mouse-move)
  (pc/mouse-on :EVENT_MOUSEWHEEL mouse-wheel)
  (pc/mouse-on :EVENT_MOUSEDOWN mouse-down)
  (pc/mouse-on :EVENT_MOUSEUP mouse-up)
  (j/call js/document :addEventListener "mouseout" mouse-out)
  (j/call js/document :addEventListener "mouseover" mouse-over)
  (-> js/document
      (j/call :getElementById "application-canvas")
      (j/call :addEventListener "auxclick"
              (fn [e]
                (when (= 2 (.-which e))
                  (swap! state assoc
                         :wheel-x 0
                         :wheel-clicked? true)))
              false)))

(defn- get-world-point []
  (let [from (pc/get-pos (.-parent entity))
        to (pc/get-pos (:ray-end @state))
        hit (pc/raycast-first from to)
        collision (some-> hit .-entity .-name)]
    (if (and hit (or (= collision "terrain")
                     (= collision "collision_rock")
                     (= collision "collision_big_tree")))
      ^js/pc.Vec3 (.-point hit)
      to)))

(defn- post-update-fn [dt this]
  (let [origin-entity (.-parent entity)
        eulers (:eulers @state)
        target-y (mod (+ (.-x eulers) 180) 360)
        target-x (-> (.-y eulers)
                     (mod 360)
                     (-))
        target-x (if (> target-x -5)
                   (do
                     (set! (.-y eulers) 5)
                     -5)
                   target-x)
        target-x (if (< target-x -55)
                   (do
                     (set! (.-y eulers) 55)
                     -55)
                   target-x)
        target-angle (:target-angle @state)
        page-x (:page-x @state)]
    (pc/setv target-angle target-x target-y 0)
    (pc/set-euler origin-entity target-angle)
    (pc/set-pos entity (get-world-point))
    (pc/look-at entity (pc/get-pos origin-entity))
    (when (and (:mouse-over? @state)
               (not (:right-click? @state))
               page-x
               (<= page-x (:mouse-edge-range @state)))
      (set! (.-x eulers) (+ (.-x eulers) (* dt (:rotation-speed @state)))))
    (when (and (:mouse-over? @state)
               (not (:right-click? @state))
               page-x
               (>= page-x (- js/window.innerWidth (:mouse-edge-range @state))))
      (set! (.-x eulers) (- (.-x eulers) (* dt (:rotation-speed @state)))))
    (when (:wheel-clicked? @state)
      (if (>= (:wheel-x @state) 180)
        (do
          (swap! state assoc
                 :wheel-clicked? false
                 :wheel-x 0)
          (set! (.-x eulers) (mod (.-x eulers) 360)))
        (let [factor (* dt 175)]
          (when (< (+ factor (:wheel-x @state)) 180)
            (set! (.-x eulers) (+ (.-x eulers) factor))
            (swap! state update :wheel-x + factor)))))))

(defn init []
  (pc/create-script :camera
                    {:init (fnt (init-fn this))
                     :post-update (fnt (post-update-fn dt this))}))
