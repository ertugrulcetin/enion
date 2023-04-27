(ns enion-cljs.scene.entities.camera
  (:require
    [applied-science.js-interop :as j]
    [enion-cljs.scene.pc :as pc :refer [app]]
    [enion-cljs.scene.states :as st]
    [enion-cljs.scene.utils :as utils])
  (:require-macros
    [enion-cljs.scene.macros :refer [fnt]]))

(defonce state (clj->js {:eulers (pc/vec3 -10659.52 13.32  0)
                         :mouse-edge-range 15
                         :mouse-over? false
                         :camera-rotation-speed 10
                         :edge-scroll-speed 100
                         :page-x nil
                         :right-click? false
                         :target-angle (pc/vec3)
                         :wheel-clicked? false
                         :wheel-x 0
                         :time 0
                         :time-since-last-shake 0
                         :last-shake-time nil}))

(comment
  (j/assoc! state :mouse-edge-range 15)
  (j/assoc! state :camera-rotation-speed 10)
  (j/assoc! state :edge-scroll-speed 100)
  (j/get state :target-angle)
  (j/get state :eulers)

  (j/assoc! state :eulers (pc/vec3 -10659.52 13.32  0))

  (j/assoc! state :eulers (pc/vec3 -10285.025 16218.33 0))

  (js/console.log entity)
  st/player
  )

(defonce entity nil)

(defn- mouse-move [e]
  (j/assoc! state :page-x (j/get e :x))
  (when (j/get state :right-click?)
    (j/update-in! state [:eulers :x] - (mod (/ (* (j/get state :camera-rotation-speed) (j/get e :dx)) 60) 360))
    (j/update-in! state [:eulers :y] + (mod (/ (* (j/get state :camera-rotation-speed) (j/get e :dy)) 60) 360))
    (when (and (not (utils/tutorial-finished? :how-to-rotate-camera?)) (not (j/get state :right-mouse-dragged?)))
      (j/assoc! state :right-mouse-dragged? true)
      (utils/finish-tutorial-step :how-to-rotate-camera?))))

(defn- mouse-wheel [e]
  #_(when-not (j/get st/settings :on-ui-element?)
    (let [ray-end (j/get state :ray-end)
          ray-pos (pc/get-loc-pos ray-end)
          z (+ (j/get ray-pos :z) (* 0.1 (j/get e :wheelDelta)))
          z (cond
              (< z 0.5) 0.5
              (> z 4) 2.5
              :else z)]
      (pc/set-loc-pos ray-end (j/get ray-pos :x) (j/get ray-pos :y) z))))

(defn- mouse-down [e]
  (when (= (j/get e :button) (:MOUSEBUTTON_RIGHT pc/key->code))
    (j/assoc! state :right-click? true)))

(defn- mouse-up [e]
  (when (= (j/get e :button) (:MOUSEBUTTON_RIGHT pc/key->code))
    (j/assoc! state :right-click? false)))

(defn- mouse-out []
  (j/assoc! state
            :mouse-over? false
            :right-click? false))

(defn- mouse-over []
  (j/assoc! state :mouse-over? true))

(defn- set-default-ray-end-z []
  (let [ray-end (j/get state :ray-end)
        ray-pos (pc/get-loc-pos ray-end)
        z 1.5]
    (pc/set-loc-pos ray-end (j/get ray-pos :x) (j/get ray-pos :y) z)))

(defn register-camera-mouse-events []
  (pc/on-mouse :EVENT_MOUSEMOVE mouse-move)
  (pc/on-mouse :EVENT_MOUSEWHEEL mouse-wheel)
  (pc/on-mouse :EVENT_MOUSEDOWN mouse-down)
  (pc/on-mouse :EVENT_MOUSEUP mouse-up))

(defn- init-fn [this]
  (j/assoc! state :ray-end (pc/find-by-name "ray_end"))
  (set-default-ray-end-z)
  (set! entity (j/get this :entity))
  (set! st/camera-entity entity)
  (register-camera-mouse-events)
  (j/call js/document :addEventListener "mouseout" mouse-out)
  (j/call js/document :addEventListener "mouseover" mouse-over)
  (-> js/document
      (j/call :getElementById "application-canvas")
      (j/call :addEventListener "auxclick"
              (fn [e]
                (when (= 2 (j/get e :which))
                  (j/assoc! state
                            :wheel-x 0
                            :wheel-clicked? true)))
              false)))

(defn- get-world-point []
  (let [from (pc/get-pos (j/get entity :parent))
        to (pc/get-pos (j/get state :ray-end))
        hit (pc/raycast-first from to)
        collision (some-> hit (j/get-in [:entity :name]))]
    (if (and hit (or (= collision "terrain")
                     (= collision "collision_rock")
                     (= collision "collision_big_tree")
                     (= collision "collision_building")))
      (j/get hit :point)
      to)))

(let [duration 1
      shake-interval 0.05
      max-shake-distance 1
      random (j/get-in js/pc [:math :random])]
  (defn update-fn [dt]
    (when (j/get state :shaking?)
      (j/update! state :time + dt)
      (let [time (j/get state :time)]
        (if (< (j/get state :time) duration)
          (do
            (j/update! state :time-since-last-shake + dt)
            (if (>= (j/get state :time-since-last-shake) shake-interval)
              (let [v (- 1 (pc/clamp (/ time duration) 0 1))
                    t (* 2 Math/PI (random 0 1))
                    u (+ (* (random 0 max-shake-distance) v) (* (random 0 max-shake-distance) v))
                    r (if (> u 1) (- 2 u) u)
                    x (+ (* r (js/Math.cos t)))
                    y (* r (js/Math.sin t))
                    start-euler (j/get state :start-euler)]
                (pc/set-loc-euler entity (+ (j/get start-euler :x) x) (+ (j/get start-euler :y) y) (j/get start-euler :z))
                (j/update! state :time-since-last-shake - shake-interval))))
          (j/assoc! state :shaking? false))))))

(defn shake-camera []
  (when (or (nil? (j/get state :last-shake-time))
            (> (- (js/Date.now) (j/get state :last-shake-time)) 30000))
    (j/assoc-in! state [:start-euler] (pc/clone (pc/get-loc-euler entity)))
    (j/assoc! state :time 0
              :shaking? true
              :last-shake-time (js/Date.now))))

(def camera-y-up-limit 3)

(defn- post-update-fn [dt _]
  (let [origin-entity (j/get entity :parent)
        eulers (j/get state :eulers)
        target-y (mod (+ (j/get eulers :x) 180) 360)
        target-x (mod (j/get eulers :y) 360)
        target-x (if (> target-x 200) camera-y-up-limit target-x)
        target-x (pc/clamp target-x camera-y-up-limit 50)
        _ (j/assoc! eulers :y target-x)
        target-angle (j/get state :target-angle)
        page-x (j/get state :page-x)]
    (pc/setv target-angle (- target-x) target-y 0)
    (pc/set-pos entity (get-world-point))
    (when-not (j/get state :shaking?)
      (pc/set-euler origin-entity target-angle)
      (pc/look-at entity (pc/get-pos origin-entity))
      (let [p (pc/get-loc-pos entity)]
        (pc/set-loc-pos entity (+ (j/get p :x) 0.3) (j/get p :y) (j/get p :z))))
    (when (and (j/get state :mouse-over?)
               (not (j/get state :right-click?))
               page-x
               (or (<= page-x (j/get state :mouse-edge-range))
                   (and (st/chat-closed?) (pc/pressed? :KEY_Q) (j/get st/player :username))))
      (j/update! eulers :x + (* dt (j/get state :edge-scroll-speed)))
      (when (and (not (utils/tutorial-finished? :rotate-camera-to-the-left))
                 (and (st/chat-closed?) (pc/pressed? :KEY_Q) (j/get st/player :username)))
        (utils/finish-tutorial-step :rotate-camera-to-the-left)))
    (when (and (j/get state :mouse-over?)
               (not (j/get state :right-click?))
               page-x
               (or (>= page-x (- js/window.innerWidth (j/get state :mouse-edge-range)))
                   (and (st/chat-closed?) (pc/pressed? :KEY_E) (j/get st/player :username))))
      (j/update! eulers :x - (* dt (j/get state :edge-scroll-speed)))
      (when (and (not (utils/tutorial-finished? :rotate-camera-to-the-right))
                 (and (st/chat-closed?) (pc/pressed? :KEY_E) (j/get st/player :username)))
        (utils/finish-tutorial-step :rotate-camera-to-the-right)))
    (when (j/get state :wheel-clicked?)
      (if (>= (j/get state :wheel-x) 180)
        (do
          (j/assoc! state
                    :wheel-clicked? false
                    :wheel-x 0)
          (j/update! eulers :x mod 360))
        (let [factor (* dt 175)]
          (when (< (+ factor (j/get state :wheel-x)) 180)
            (j/update! eulers :x + factor)
            (j/update! state :wheel-x + factor)))))))

(defn init []
  (pc/create-script :camera
                    {:init (fnt (init-fn this))
                     :update (fnt (update-fn dt))
                     :post-update (fnt (post-update-fn dt this))}))
