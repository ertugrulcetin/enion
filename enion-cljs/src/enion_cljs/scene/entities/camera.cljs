(ns enion-cljs.scene.entities.camera
  (:require
    [applied-science.js-interop :as j]
    [enion-cljs.scene.pc :as pc :refer [app]])
  (:require-macros
    [enion-cljs.scene.macros :refer [fnt]]))

(defonce state (clj->js {:eulers (pc/vec3)
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
  (j/assoc! state :page-x (j/get e :x))
  (when (j/get state :right-click?)
    (j/update-in! state [:eulers :x] - (mod (/ (* (j/get state :mouse-speed) (j/get e :dx)) 60) 360))
    (j/update-in! state [:eulers :y] + (mod (/ (* (j/get state :mouse-speed) (j/get e :dy)) 60) 360))))

(defn- mouse-wheel [e]
  (let [ray-end (j/get state :ray-end)
        ray-pos (pc/get-loc-pos ray-end)
        z (+ (j/get ray-pos :z) (* 0.1 (j/get e :wheelDelta)))
        z (cond
            (< z 0.5) 0.5
            (> z 3.5) 3.5
            :else z)]
    (pc/set-loc-pos ray-end (j/get ray-pos :x) (j/get ray-pos :y) z)))

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

(defn- init-fn [this]
  (j/assoc! state :ray-end (pc/find-by-name "ray_end"))
  (set! entity (j/get this :entity))
  (pc/on-mouse :EVENT_MOUSEMOVE mouse-move)
  (pc/on-mouse :EVENT_MOUSEWHEEL mouse-wheel)
  (pc/on-mouse :EVENT_MOUSEDOWN mouse-down)
  (pc/on-mouse :EVENT_MOUSEUP mouse-up)
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

(defn- post-update-fn [dt _]
  (let [origin-entity (j/get entity :parent)
        eulers (j/get state :eulers)
        target-y (mod (+ (j/get eulers :x) 180) 360)
        target-x (-> (j/get eulers :y)
                     (mod 360)
                     (-))
        target-x (if (> target-x -5)
                   (do
                     (j/assoc! eulers :y 5)
                     -5)
                   target-x)
        target-x (if (< target-x -55)
                   (do
                     (j/assoc! eulers :y 55)
                     -55)
                   target-x)
        target-angle (j/get state :target-angle)
        page-x (j/get state :page-x)]
    (pc/setv target-angle target-x target-y 0)
    (pc/set-euler origin-entity target-angle)
    (pc/set-pos entity (get-world-point))
    (pc/look-at entity (pc/get-pos origin-entity))
    (when (and (j/get state :mouse-over?)
               (not (j/get state :right-click?))
               page-x
               (<= page-x (j/get state :mouse-edge-range)))
      (j/update! eulers :x + (* dt (j/get state :rotation-speed))))
    (when (and (j/get state :mouse-over?)
               (not (j/get state :right-click?))
               page-x
               (>= page-x (- js/window.innerWidth (j/get state :mouse-edge-range))))
      (j/update! eulers :x - (* dt (j/get state :rotation-speed))))
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
                     :post-update (fnt (post-update-fn dt this))}))
