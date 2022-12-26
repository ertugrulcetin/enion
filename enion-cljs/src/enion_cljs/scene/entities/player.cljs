(ns enion-cljs.scene.entities.player
  (:require
    [applied-science.js-interop :as j]
    [enion-cljs.scene.animations.asas :as anim.asas]
    [enion-cljs.scene.animations.core :as anim]
    [enion-cljs.scene.animations.mage :as anim.mage]
    [enion-cljs.scene.animations.priest :as anim.priest]
    [enion-cljs.scene.animations.warrior :as anim.warrior]
    [enion-cljs.scene.entities.camera :as entity.camera]
    [enion-cljs.scene.keyboard :as k]
    [enion-cljs.scene.pc :as pc]
    [enion-cljs.scene.skills.mage :as skills.mage])
  (:require-macros
    [enion-cljs.scene.macros :refer [fnt]]))

;; TODO apply restitution and friction to 1 - collision of other enemies
(defonce state (clj->js {:speed 750
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
(defonce effects (atom {}))

(defn- pressing-wasd-or-has-target? []
  (or (k/pressing-wasd?) (j/get state :target-pos-available?)))

(defn- process-running []
  (if (pressing-wasd-or-has-target?)
    (pc/set-anim-boolean model-entity "run" true)
    (pc/set-anim-boolean model-entity "run" false)))

(defn- register-keyboard-events []
  (anim/register-key->skills)
  (pc/on-keyboard :EVENT_KEYDOWN
                  (fn [e]
                    ;; (anim.warrior/process-skills e state)
                    (anim.asas/process-skills e state)
                    ;; (anim.mage/process-skills e state)
                    ;; (anim.priest/process-skills e state)
                    ))
  (pc/on-keyboard :EVENT_KEYUP
                  (fn [e]
                    (process-running)))
  (anim/register-anim-events state
                             anim.asas/events
                             ;; anim.warrior/events
                             ;; anim.priest/events
                             player-entity))

;; TODO also need to check is char dead or alive to be able to do that
(defn- set-target-position [e]
  (let [x (j/get e :x)
        y (j/get e :y)
        camera (j/get entity.camera/entity :camera)
        from (pc/get-pos entity.camera/entity)
        to (pc/screen-to-world camera x y)
        result (pc/raycast-first from to)]
    (when (and result (= "terrain" (j/get-in result [:entity :name])))
      (let [x (j/get-in result [:point :x])
            y (j/get-in result [:point :y])
            z (j/get-in result [:point :z])]
        (pc/look-at model-entity x (j/get (pc/get-pos model-entity) :y) z true)
        (skills.mage/throw-nova (pc/find-by-name "nova") (j/get result :point))
        (j/assoc! state :target-pos (pc/setv (j/get state :target-pos) x y z)
                  :target-pos-available? true)
        (process-running)))))

(defn- register-mouse-events []
  (pc/on-mouse :EVENT_MOUSEDOWN
               (fn [e]
                 (when (and (pc/button? e :MOUSEBUTTON_LEFT)
                            (or (= "CANVAS" (j/get-in e [:element :nodeName]))
                                (not= "all" (-> (j/call js/window :getComputedStyle (j/get e :element))
                                                (j/get :pointerEvents)))))
                   (j/assoc! state :mouse-left-locked? true)
                   (set-target-position e))))
  (pc/on-mouse :EVENT_MOUSEUP
               (fn [e]
                 (when (pc/button? e :MOUSEBUTTON_LEFT)
                   (j/assoc! state :mouse-left-locked? false))))
  (pc/on-mouse :EVENT_MOUSEMOVE
               (fn [e]
                 (when (j/get state :mouse-left-locked?)
                   (set-target-position e)))))

(defn- collision-start [result]
  (when (= "terrain" (j/get-in result [:other :name]))
    (j/assoc! state :on-ground? true)))

(defn- collision-end [result]
  (when (= "terrain" (j/get result :name))
    (j/assoc! state :on-ground? false)))

(defn- register-collision-events [entity]
  (j/call-in entity [:collision :on] "collisionstart" collision-start)
  (j/call-in entity [:collision :on] "collisionend" collision-end))

(defn- init-fn [this]
  (let [character-template-entity (pc/clone (pc/find-by-name "orc_asas"))
        player-entity* (j/get this :entity)
        _ (pc/set-loc-pos character-template-entity 0 0 0)
        _ (pc/add-child player-entity* character-template-entity)
        model-entity* (pc/find-by-name* character-template-entity "orc_asas_model")]
    (j/assoc! state :camera (pc/find-by-name "camera"))
    (set! player-entity player-entity*)
    (set! model-entity model-entity*)
    (set! anim/model-entity model-entity*)
    (register-keyboard-events)
    (register-mouse-events)
    (register-collision-events player-entity*)))

;; TODO add if entity is able to move - like app-focused? and alive? etc.
(defn- process-movement [_ _]
  (let [speed (j/get state :speed)
        camera (j/get state :camera)
        right (j/get camera :right)
        forward (j/get camera :forward)
        world-dir (j/get state :world-dir)
        temp-dir (j/get state :temp-dir)]
    (when-not (anim/char-cant-run?)
      (if (j/get state :target-pos-available?)
        (let [target (j/get state :target-pos)
              temp-dir (pc/copyv temp-dir target)
              pos (pc/get-pos player-entity)
              dir (-> temp-dir (pc/sub pos) pc/normalize (pc/scale speed))]
          (if (>= (pc/distance target pos) 0.2)
            (pc/apply-force player-entity (.-x dir) 0 (.-z dir))
            (do
              (j/assoc! state :target-pos-available? false)
              (process-running))))
        (do
          (pc/setv world-dir 0 0 0)
          (j/assoc! state :x 0 :z 0 :target-y (j/get-in entity.camera/state [:eulers :x]))
          (when (pc/pressed? :KEY_W)
            (j/update! state :z inc))
          (when (pc/pressed? :KEY_A)
            (j/update! state :x dec))
          (when (pc/pressed? :KEY_S)
            (j/update! state :z dec))
          (when (pc/pressed? :KEY_D)
            (j/update! state :x inc))

          (when (or (not= (j/get state :x) 0)
                    (not= (j/get state :z) 0))
            (pc/addv world-dir (pc/mul-scalar (pc/copyv temp-dir forward) (j/get state :z)))
            (pc/addv world-dir (pc/mul-scalar (pc/copyv temp-dir right) (j/get state :x)))
            (-> world-dir pc/normalize (pc/scale speed))
            (pc/apply-force player-entity (j/get world-dir :x) 0 (j/get world-dir :z)))

          (cond
            (and (pc/pressed? :KEY_A) (pc/pressed? :KEY_W)) (j/update! state :target-y + 45)
            (and (pc/pressed? :KEY_D) (pc/pressed? :KEY_W)) (j/update! state :target-y - 45)
            (and (pc/pressed? :KEY_A) (pc/pressed? :KEY_S)) (j/update! state :target-y + 135)
            (and (pc/pressed? :KEY_D) (pc/pressed? :KEY_S)) (j/update! state :target-y - 135)
            (pc/pressed? :KEY_A) (j/update! state :target-y + 90)
            (pc/pressed? :KEY_D) (j/update! state :target-y - 90)
            (pc/pressed? :KEY_S) (j/update! state :target-y + 180))
          (when (pressing-wasd-or-has-target?)
            (pc/set-loc-euler model-entity 0 (j/get state :target-y) 0)
            (pc/set-anim-boolean model-entity "run" true)))))))

(defn get-position []
  (pc/get-pos player-entity))

(defn get-state []
  (pc/get-anim-state model-entity))

(defn- update-fn [dt this]
  (process-movement dt this))

(defn init []
  (pc/create-script :player
                    {:init (fnt (init-fn this))
                     :update (fnt (update-fn dt this))}))

(comment
  (get-position)
  (js/console.log player-entity)
  (j/call-in player-entity [:rigidbody :teleport] 31 2.3 -32)

  (j/assoc! state :speed 650)

  (pc/get-map-pos player-entity)

  (pc/apply-impulse player-entity 0 200 0)

  (j/get state :on-ground?)

  )
