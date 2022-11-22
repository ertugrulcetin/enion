(ns enion-cljs.scene.entities.player
  (:require
   [enion-cljs.scene.pc :as pc :refer [app]]
   [applied-science.js-interop :as j])
  (:require-macros
   [enion-cljs.scene.macros :refer [fnt]]))

(defonce state (atom {}))
(defonce entity nil)

(defn- init-fn [this]
  (let [camera (pc/find-by-name "Camera")]
    (swap! state assoc
      :speed (j/get this :speed)
      :camera camera
      :camera-script (j/get-in camera [:script :cameraMovement])
      :model-entity (pc/find-by-name "model")
      :eulers (pc/vec3)
      :force (pc/vec3)
      :x (atom 0)
      :z (atom 0)
      :target-y (atom nil))
    (set! entity (j/get this :entity))))

;;TODO add if entity is able to move - like app-focused? and alive? etc.
(defn- process-movement [_ _]
  (let [force (:force @state)
        speed (:speed @state)
        forward (.-forward (:camera @state))
        right (.-right (:camera @state))
        x (:x @state)
        z (:z @state)
        target-y (:target-y @state)]
    (reset! x 0)
    (reset! z 0)
    (reset! target-y (+ (.-eulers.x ^js/pc.ScriptComponent (:camera-script @state)) 20))
    (when (pc/pressed? :KEY_W)
      (swap! x + (.-x forward))
      (swap! z + (.-z forward)))
    (when (pc/pressed? :KEY_A)
      (swap! x - (.-x right))
      (swap! z - (.-z right)))
    (when (pc/pressed? :KEY_S)
      (swap! x - (.-x forward))
      (swap! z - (.-z forward)))
    (when (pc/pressed? :KEY_D)
      (swap! x + (.-x right))
      (swap! z + (.-z right)))
    (when (pc/pressed? :KEY_SPACE)
      (.applyImpulse ^js/pc.RigidBodyComponent (.-rigidbody entity) 0 40 0))
    (when (and (not= 0 @x) (not= 0 @z))
      (.. force (set @x 0 @z) normalize (scale speed))
      (.applyForce ^js/pc.RigidBodyComponent (.-rigidbody entity) force))
    (when (and (pc/pressed? :KEY_A) (pc/pressed? :KEY_W))
      (swap! target-y + 45))
    (cond
      (and (pc/pressed? :KEY_A) (pc/pressed? :KEY_W)) (swap! target-y + 45)
      (and (pc/pressed? :KEY_D) (pc/pressed? :KEY_W)) (swap! target-y - 45)
      (and (pc/pressed? :KEY_A) (pc/pressed? :KEY_S)) (swap! target-y + 135)
      (and (pc/pressed? :KEY_D) (pc/pressed? :KEY_S)) (swap! target-y - 135)
      (pc/pressed? :KEY_A) (swap! target-y + 90)
      (pc/pressed? :KEY_D) (swap! target-y - 90)
      (pc/pressed? :KEY_S) (swap! target-y + 180))
    (when (or (pc/pressed? :KEY_W)
            (pc/pressed? :KEY_A)
            (pc/pressed? :KEY_S)
            (pc/pressed? :KEY_D))
      (.setLocalEulerAngles ^js/pc.Entity (:model-entity @state) 0 @target-y 0))))

(defn- update-fn [dt this]
  (process-movement dt this))

(defn init []
  (pc/create-script :player
    {:attrs {:speed {:type "number"
                     :default 750}}
     :init (fnt (init-fn this))
     :update (fnt (update-fn dt this))}))

(comment
  (js/console.log entity)
  ;this.entity.rigidbody.applyImpulse(0, 80, 0);
  (j/call-in entity [:rigidbody :teleport] -0 2.3 0)
  )