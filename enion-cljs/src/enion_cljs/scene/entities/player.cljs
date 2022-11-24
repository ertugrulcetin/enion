(ns enion-cljs.scene.entities.player
  (:require
    [applied-science.js-interop :as j]
    [enion-cljs.scene.entities.camera :as entity.camera]
    [enion-cljs.scene.pc :as pc])
  (:require-macros
    [enion-cljs.scene.macros :refer [fnt]]))

(defonce state (atom {:speed 750
                      :x 0
                      :z 0
                      :target-y nil
                      :eulers (pc/vec3)
                      :force (pc/vec3)}))

(defonce entity nil)

(defn- init-fn [this]
  (swap! state assoc
         :camera (pc/find-by-name "Camera")
         :model-entity (pc/find-by-name "model"))
  (set! entity (j/get this :entity)))

;; TODO add if entity is able to move - like app-focused? and alive? etc.
(defn- process-movement [_ _]
  (let [force (:force @state)
        speed (:speed @state)
        forward (.-forward (:camera @state))
        right (.-right (:camera @state))]
    ;; TODO fix movement
    (swap! state assoc
           :x 0
           :z 0
           :target-y (+ (.-x (:eulers @entity.camera/state)) 20))
    (when (pc/pressed? :KEY_W)
      (swap! state update :x + (.-x forward))
      (swap! state update :z + (.-z forward)))
    (when (pc/pressed? :KEY_A)
      (swap! state update :x - (.-x right))
      (swap! state update :z - (.-z right)))
    (when (pc/pressed? :KEY_S)
      (swap! state update :x - (.-x forward))
      (swap! state update :z - (.-z forward)))
    (when (pc/pressed? :KEY_D)
      (swap! state update :x + (.-x right))
      (swap! state update :z + (.-z right)))
    (when (pc/pressed? :KEY_SPACE)
      (.applyImpulse ^js/pc.RigidBodyComponent (.-rigidbody entity) 0 40 0))
    (when (and (not= 0 (:x @state)) (not= 0 (:z @state)))
      (.. force (set (:x @state) 0 (:z @state)) normalize (scale speed))
      (.applyForce ^js/pc.RigidBodyComponent (.-rigidbody entity) force))
    (cond
      (and (pc/pressed? :KEY_A) (pc/pressed? :KEY_W)) (swap! state update :target-y + 45)
      (and (pc/pressed? :KEY_D) (pc/pressed? :KEY_W)) (swap! state update :target-y - 45)
      (and (pc/pressed? :KEY_A) (pc/pressed? :KEY_S)) (swap! state update :target-y + 135)
      (and (pc/pressed? :KEY_D) (pc/pressed? :KEY_S)) (swap! state update :target-y - 135)
      (pc/pressed? :KEY_A) (swap! state update :target-y + 90)
      (pc/pressed? :KEY_D) (swap! state update :target-y - 90)
      (pc/pressed? :KEY_S) (swap! state update :target-y + 180))
    (when (or (pc/pressed? :KEY_W)
              (pc/pressed? :KEY_A)
              (pc/pressed? :KEY_S)
              (pc/pressed? :KEY_D))
      (.setLocalEulerAngles ^js/pc.Entity (:model-entity @state) 0 (:target-y @state) 0))))

(defn- update-fn [dt this]
  (process-movement dt this))

(defn init []
  (pc/create-script :player
                    {:init (fnt (init-fn this))
                     :update (fnt (update-fn dt this))}))

(comment
  (js/console.log entity)
  ;this.entity.rigidbody.applyImpulse(0, 80, 0);
  (j/call-in entity [:rigidbody :teleport] -0 2.3 0)
  )
