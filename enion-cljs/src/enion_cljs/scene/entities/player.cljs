(ns enion-cljs.scene.entities.player
  (:require
    [applied-science.js-interop :as j]
    [enion-cljs.scene.entities.camera :as entity.camera]
    [enion-cljs.scene.pc :as pc])
  (:require-macros
    [enion-cljs.scene.macros :refer [fnt]]))

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

(defn- pressing-wasd? []
  (or (pc/pressed? :KEY_W)
      (pc/pressed? :KEY_A)
      (pc/pressed? :KEY_S)
      (pc/pressed? :KEY_D)))

(defn- pressing-attacks? []
  (or (pc/pressed? :KEY_1)
      (pc/pressed? :KEY_R)))

(defn- pressing-wasd-or-has-target? []
  (or (pressing-wasd?) (:target-pos-available? @state)))

;; jump has Exit Time = 1
;; TODO when pressed in gets triggered always
;; TODO can't attack while W pressed
(defn- process-skills [e]
  (when-not (-> e .-event .-repeat)
    (let [active-state (pc/get-anim-state model-entity)]
      (when (pressing-wasd?)
        (swap! state assoc :target-pos-available? false)
        (when (and (= active-state "attackOneHand") (not (:skill-locked? @state)) (not (pressing-attacks?)))
          (pc/set-anim-boolean model-entity "attack_one_hand" false)
          (pc/set-anim-boolean model-entity "run" true)))
      (cond
        (and (= active-state "attackOneHand")
             (= (.-key e) (pc/get-code :KEY_R))
             (:can-r-attack-interrupt? @state))
        (do
          (println "BETWEEN COMBO!!")
          (pc/set-anim-boolean model-entity "attack_one_hand" false)
          (pc/set-anim-boolean model-entity "attack_r" true))

        (and (= "idle" active-state)
             (pressing-wasd-or-has-target?))
        (pc/set-anim-boolean model-entity "run" true)

        (and (#{"idle" "run"} active-state)
             (= (.-key e) (pc/get-code :KEY_SPACE)))
        (pc/set-anim-boolean model-entity "jump" true)

        (and (#{"idle" "run"} active-state)
             (= (.-key e) (pc/get-code :KEY_1)))
        (pc/set-anim-boolean model-entity "attack_one_hand" true)

        (and (#{"idle" "run"} active-state)
             (= (.-key e) (pc/get-code :KEY_2)))
        (pc/set-anim-boolean model-entity "attack_slow_down" true)

        (and (#{"idle" "run"} active-state)
             (= (.-key e) (pc/get-code :KEY_R)))
        (pc/set-anim-boolean model-entity "attack_r" true)))))

(defn- process-running []
  (if (pressing-wasd-or-has-target?)
    (pc/set-anim-boolean model-entity "run" true)
    (pc/set-anim-boolean model-entity "run" false)))

(defn on-jump-end [e]
  (pc/set-anim-boolean model-entity (.-string e) false)
  (when (pressing-wasd-or-has-target?)
    (pc/set-anim-boolean model-entity "run" true)))

(defn on-attack-one-hand-end [e]
  (pc/set-anim-boolean model-entity (.-string e) false)
  (swap! state assoc
         :skill-locked? false
         :can-r-attack-interrupt? false)
  (when (pressing-wasd-or-has-target?)
    (pc/set-anim-boolean model-entity "run" true)))

(defn on-attack-slow-down-end [e]
  (pc/set-anim-boolean model-entity (.-string e) false)
  (when (pressing-wasd-or-has-target?)
    (pc/set-anim-boolean model-entity "run" true)))

(defn on-attack-r-end [e]
  (pc/set-anim-boolean model-entity (.-string e) false)
  (when (pressing-wasd-or-has-target?)
    (pc/set-anim-boolean model-entity "run" true)))

(defn- register-skill-events []
  (pc/on-keyboard :EVENT_KEYDOWN (fn [e]
                                   (process-skills e)))
  (pc/on-keyboard :EVENT_KEYUP (fn [e]
                                 (process-running)))
  (pc/on-anim model-entity "on-jump-end"
              (fn [e]
                (on-jump-end e)))

  (pc/on-anim model-entity "on-attack-one-hand-end"
              (fn [e]
                (on-attack-one-hand-end e)))

  (pc/on-anim model-entity "on-attack-slow-down-end"
              (fn [e]
                (on-attack-slow-down-end e)))

  (pc/on-anim model-entity "on-attack-r-end"
              (fn [e]
                (on-attack-r-end e)))

  (pc/on-anim model-entity "on-attack-one-hand-end-call"
              (fn [e]
                (println "on-attack-one-hand-end-call")
                (swap! state assoc :skill-locked? true)))

  (pc/on-anim model-entity "on-attack-one-hand-end-combo-lock-release"
              (fn [e]
                (println "on-attack-one-hand-end-combo-lock-release")
                (swap! state assoc :can-r-attack-interrupt? true)))

  (pc/on-anim model-entity "on-attack-one-hand-end-combo-lock"
              (fn [e]
                (println "on-attack-one-hand-end-combo-lock")
                (swap! state assoc :can-r-attack-interrupt? false)))

  (pc/on-anim model-entity "on-idle-start"
              (fn [e]
                (println "on idle start")
                (swap! state assoc :skill-locked? false)))

  (pc/on-anim model-entity "on-run-start"
              (fn [e]
                (println "on run start")
                (swap! state assoc :skill-locked? false))))

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
                 (when (= (.-button e) (pc/get-code :MOUSEBUTTON_LEFT))
                   (swap! state assoc :mouse-left-locked? true)
                   (set-target-position e))))
  (pc/on-mouse :EVENT_MOUSEUP
               (fn [e]
                 (when (= (.-button e) (pc/get-code :MOUSEBUTTON_LEFT))
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
  ;this.entity.rigidbody.applyImpulse(0, 80, 0);
  (j/call-in player-entity [:rigidbody :teleport] 31 2.3 -32)
  (swap! state assoc :speed 1750)
  )
