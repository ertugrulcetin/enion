(ns enion-cljs.scene.entities.player
  (:require
    [applied-science.js-interop :as j]
    [enion-cljs.scene.animations.core :as anim.core]
    [enion-cljs.scene.animations.warrior :as anim.warrior]
    [enion-cljs.scene.entities.camera :as entity.camera]
    [enion-cljs.scene.keyboard :as k]
    [enion-cljs.scene.pc :as pc])
  (:require-macros
    [enion-cljs.scene.macros :as m :refer [fnt]]))

;; TODO apply restitution and friction to 1 - collision of other enemies

(defonce state (atom {:speed 750
                      :x 0
                      :z 0
                      :target-y nil
                      :eulers (pc/vec3)
                      :temp-dir (pc/vec3)
                      :world-dir (pc/vec3)
                      :health 100
                      :mouse-left-locked? false
                      :target-pos (pc/vec3)
                      :target-pos-available? false
                      :skill-locked? false
                      :can-r-attack-interrupt? false}))

(defonce player-entity nil)
(defonce model-entity nil)

(defn- pressing-wasd-or-has-target? []
  (or (k/pressing-wasd?) (:target-pos-available? @state)))

(defn- process-running []
  (if (pressing-wasd-or-has-target?)
    (pc/set-anim-boolean model-entity "run" true)
    (pc/set-anim-boolean model-entity "run" false)))

(defn- register-skill-events []
  (pc/on-keyboard :EVENT_KEYDOWN
                  (fn [e]
                    (anim.warrior/process-skills e state)))
  (pc/on-keyboard :EVENT_KEYUP
                  (fn [e]
                    (process-running)))
  (anim.warrior/register-anim-events state model-entity pressing-wasd-or-has-target?))

;; TODO also need to check is char dead or alive to be able to do that
(defn- set-target-position [e]
  (let [x (.-x e)
        y (.-y e)
        ^js/pc.CameraComponent camera (.-camera entity.camera/entity)
        from (pc/get-pos entity.camera/entity)
        to (.screenToWorld camera x y (.-farClip camera))
        result (pc/raycast-first from to)]
    (when (and result (= "terrain" (-> result .-entity .-name)))
      (let [x (-> result .-point .-x)
            y (-> result .-point .-y)
            z (-> result .-point .-z)]
        (pc/look-at model-entity x (.-y (pc/get-pos model-entity)) z true)
        (swap! state assoc
               :target-pos (pc/setv (:target-pos @state) x y z)
               :target-pos-available? true)
        (process-running)))))

(defn- register-mouse-events []
  (pc/on-mouse :EVENT_MOUSEDOWN
               (fn [e]
                 (when (pc/button? e :MOUSEBUTTON_LEFT)
                   (swap! state assoc :mouse-left-locked? true)
                   (set-target-position e))))
  (pc/on-mouse :EVENT_MOUSEUP
               (fn [e]
                 (when (pc/button? e :MOUSEBUTTON_LEFT)
                   (swap! state assoc :mouse-left-locked? false))))
  (pc/on-mouse :EVENT_MOUSEMOVE
               (fn [e]
                 (when (:mouse-left-locked? @state)
                   (set-target-position e)))))

(defn- init-fn [this]
  (let [model-entity* (pc/find-by-name "model")]
    (swap! state assoc
           :camera (pc/find-by-name "camera")
           :model-entity model-entity*)
    (set! player-entity (j/get this :entity))
    (set! model-entity model-entity*)
    (set! anim.core/model-entity model-entity*)
    (register-skill-events)
    (register-mouse-events)))

;; TODO add if entity is able to move - like app-focused? and alive? etc.
(defn- process-movement [_ _]
  (let [speed (:speed @state)
        camera (:camera @state)
        right (.-right camera)
        forward (.-forward camera)
        world-dir (:world-dir @state)
        temp-dir (:temp-dir @state)]
    (if (:target-pos-available? @state)
      (let [target (:target-pos @state)
            temp-dir (pc/copyv temp-dir target)
            pos (pc/get-pos player-entity)
            dir (-> temp-dir (pc/sub pos) pc/normalize (pc/scale speed))]
        (if (>= (pc/distance target pos) 0.2)
          (pc/apply-force player-entity (.-x dir) 0 (.-z dir))
          (do
            (swap! state assoc :target-pos-available? false)
            (process-running))))
      (do
        (pc/setv world-dir 0 0 0)
        (swap! state assoc
               :x 0
               :z 0
               :target-y (.-x (:eulers @entity.camera/state)))
        (when (pc/pressed? :KEY_W)
          (swap! state update :z inc))
        (when (pc/pressed? :KEY_A)
          (swap! state update :x dec))
        (when (pc/pressed? :KEY_S)
          (swap! state update :z dec))
        (when (pc/pressed? :KEY_D)
          (swap! state update :x inc))

        (when (pc/pressed? :KEY_SPACE)
          (pc/apply-impulse player-entity 0 40 0))

        (when (or (not= (:x @state) 0)
                  (not= (:z @state) 0))
          (pc/addv world-dir (pc/mul-scalar (pc/copyv temp-dir forward) (:z @state)))
          (pc/addv world-dir (pc/mul-scalar (pc/copyv temp-dir right) (:x @state)))
          (-> world-dir pc/normalize (pc/scale speed))
          (pc/apply-force player-entity (.-x world-dir) 0 (.-z world-dir)))

        (cond
          (and (pc/pressed? :KEY_A) (pc/pressed? :KEY_W)) (swap! state update :target-y + 45)
          (and (pc/pressed? :KEY_D) (pc/pressed? :KEY_W)) (swap! state update :target-y - 45)
          (and (pc/pressed? :KEY_A) (pc/pressed? :KEY_S)) (swap! state update :target-y + 135)
          (and (pc/pressed? :KEY_D) (pc/pressed? :KEY_S)) (swap! state update :target-y - 135)
          (pc/pressed? :KEY_A) (swap! state update :target-y + 90)
          (pc/pressed? :KEY_D) (swap! state update :target-y - 90)
          (pc/pressed? :KEY_S) (swap! state update :target-y + 180))
        (when (pressing-wasd-or-has-target?)
          (pc/set-loc-euler model-entity 0 (:target-y @state) 0))))))

(defn- update-fn [dt this]
  (process-movement dt this))

(defn init []
  (pc/create-script :player
                    {:init (fnt (init-fn this))
                     :update (fnt (update-fn dt this))}))

(comment
  (js/console.log player-entity)
  (j/call-in player-entity [:rigidbody :teleport] 31 2.3 -32)
  (swap! state assoc :speed 1750)

  (def ddd (clj->js {:a 1}))

  (m/assoc! ddd :a-ses? 3)
  (m/update! ddd :a-ses? inc)
  (m/get! ddd :a-ses?)
  )
