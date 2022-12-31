(ns enion-cljs.scene.entities.player
  (:require
    [applied-science.js-interop :as j]
    [enion-cljs.common :as common]
    [enion-cljs.scene.entities.camera :as entity.camera]
    [enion-cljs.scene.keyboard :as k]
    [enion-cljs.scene.pc :as pc]
    [enion-cljs.scene.skills.asas :as skills.asas]
    [enion-cljs.scene.skills.core :as skills]
    [enion-cljs.scene.skills.mage :as skills.mage]
    [enion-cljs.scene.skills.mage :as skills.mage]
    [enion-cljs.scene.skills.priest :as skills.priest]
    [enion-cljs.scene.skills.warrior :as skills.warrior])
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
                         :can-r-attack-interrupt? false
                         :race :orc
                         :class :warrior
                         :name "NeaTBuSTeR"}))

(defonce player-entity nil)
(defonce model-entity nil)
(defonce effects (clj->js {}))

(defn- pressing-wasd-or-has-target? []
  (or (k/pressing-wasd?) (j/get state :target-pos-available?)))

(defn- process-running []
  (if (pressing-wasd-or-has-target?)
    (pc/set-anim-boolean model-entity "run" true)
    (pc/set-anim-boolean model-entity "run" false)))

(defn- register-keyboard-events []
  (let [class (j/get state :class)
        [process-skills events] (case class
                                  "warrior" [skills.warrior/process-skills skills.warrior/events]
                                  "asas" [skills.asas/process-skills skills.asas/events]
                                  "priest" [skills.priest/process-skills skills.priest/events]
                                  "mage" [skills.mage/process-skills skills.mage/events])]
    (skills/register-key->skills (common/skill-slot-order-by-class (keyword class)))
    (pc/on-keyboard :EVENT_KEYDOWN
                    (fn [e]
                      (process-skills e state)))
    (pc/on-keyboard :EVENT_KEYUP
                    (fn [e]
                      (process-running)))
    (skills/register-skill-events state events player-entity)
    (common/on :update-skills-order skills/register-key->skills)))

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
        (when-not (skills/char-cant-run?)
          (pc/look-at model-entity x (j/get (pc/get-pos model-entity) :y) z true))
        ;; (skills.mage/throw-nova (pc/find-by-name "nova") (j/get result :point))
        (j/assoc! state :target-pos (pc/setv (j/get state :target-pos) x y z)
                  :target-pos-available? true)
        (pc/set-locater-target x z)
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

(defn- get-model-entity [player-entity*]
  (let [race (j/get state :race)
        class (j/get state :class)
        template-entity-name (str race "_" class)
        model-entity-name (str race "_" class "_model")
        character-template-entity (pc/clone (pc/find-by-name template-entity-name))]
    (pc/set-loc-pos character-template-entity 0 0 0)
    (pc/add-child player-entity* character-template-entity)
    (pc/find-by-name character-template-entity model-entity-name)))

(defn- create-username-text [player-entity*]
  (let [username (j/get state :name)
        race (j/get state :race)
        class (j/get state :class)
        template-entity-name (str race "_" class)
        character-template-entity (pc/find-by-name player-entity* template-entity-name)
        username-text-entity (pc/clone (pc/find-by-name "char_name"))]
    (j/assoc-in! username-text-entity [:element :text] username)
    (j/assoc-in! username-text-entity [:script :enabled] true)
    (pc/add-child character-template-entity username-text-entity)
    (when (and (= race "orc")
               (or (= class "priest")
                   (= class "warrior")))
      (pc/set-loc-pos username-text-entity 0 0.05 0))))

(comment
  (let [username (j/get state :name)
        race (j/get state :race)
        class (j/get state :class)
        template-entity-name (str race "_" class)
        character-template-entity (pc/find-by-name player-entity template-entity-name)
        username-text-entity (pc/find-by-name character-template-entity "char_name")]
    ;(js/console.log username-text-entity)
    (j/assoc-in! username-text-entity [:element :color] (pc/color (/ 355 255) (/ 0 255) (/ 0 255)))
    (j/assoc-in! username-text-entity [:element :outlineThickness] 0)
    (j/assoc-in! username-text-entity [:element :fontSize] 32)

    )
  )

(defn- init-fn [this]
  (let [player-entity* (j/get this :entity)
        model-entity* (get-model-entity player-entity*)]
    (create-username-text player-entity*)
    (j/assoc! state :camera (pc/find-by-name "camera"))
    (set! player-entity player-entity*)
    (set! model-entity model-entity*)
    (set! skills/model-entity model-entity*)
    (common/fire :init-skills (keyword (j/get state :class)))
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
    (when-not (skills/char-cant-run?)
      (if (j/get state :target-pos-available?)
        (let [target (j/get state :target-pos)
              temp-dir (pc/copyv temp-dir target)
              pos (pc/get-pos player-entity)
              dir (-> temp-dir (pc/sub pos) pc/normalize (pc/scale speed))]
          (if (>= (pc/distance target pos) 0.2)
            (pc/apply-force player-entity (j/get dir :x) 0 (j/get dir :z))
            (do
              (j/assoc! state :target-pos-available? false)
              (pc/set-locater-target)
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
