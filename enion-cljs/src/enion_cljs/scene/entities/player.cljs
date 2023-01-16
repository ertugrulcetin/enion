(ns enion-cljs.scene.entities.player
  (:require
    [applied-science.js-interop :as j]
    [enion-cljs.common :as common :refer [dev?]]
    [enion-cljs.scene.entities.camera :as entity.camera]
    [enion-cljs.scene.keyboard :as k]
    [enion-cljs.scene.pc :as pc]
    [enion-cljs.scene.skills.asas :as skills.asas]
    [enion-cljs.scene.skills.core :as skills]
    [enion-cljs.scene.skills.effects :as skills.effects]
    [enion-cljs.scene.skills.mage :as skills.mage]
    [enion-cljs.scene.skills.priest :as skills.priest]
    [enion-cljs.scene.skills.warrior :as skills.warrior])
  (:require-macros
    [enion-cljs.scene.macros :refer [fnt]]))

;; TODO apply restitution and friction to 1 - collision of other enemies
(defonce state (clj->js {:speed 550
                         :x 0
                         :z 0
                         :target-y nil
                         :eulers (pc/vec3)
                         :temp-dir (pc/vec3)
                         :world-dir (pc/vec3)
                         :mouse-left-locked? false
                         :target-pos (pc/vec3)
                         :target-pos-available? false
                         :skill-locked? false
                         :can-r-attack-interrupt? false
                         :ray (pc/ray)
                         :hit-position (pc/vec3)}))

(defonce player-entity nil)
(defonce model-entity nil)

(def username-color (pc/color 2 2 2))
(def username-party-color (pc/color 2 2 0))
(def username-enemy-color (pc/color 2 0 0))

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

(defn- square [n]
  (js/Math.pow n 2))

(defn inside-circle?
  "Clojure formula of (x - center_x)² + (y - center_y)² < radius²"
  [x z center-x center-z radius]
  (< (+ (square (- x center-x)) (square (- z center-z))) (square radius)))

;; TODO also need to check is char dead or alive to be able to do that
;; TODO when char at the corner of the map, set-target-position does not work due to map wall collision
;;  this.ray.origin.copy(this.cameraEntity.getPosition());
;;  this.ray.direction.sub(this.ray.origin).normalize();

(defonce other-player nil)

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
            z (j/get-in result [:point :z])
            char-pos (pc/get-pos model-entity)]
        (when-not (skills/char-cant-run?)
          (pc/look-at model-entity x (j/get char-pos :y) z true))
        ;; ((j/get state :throw-nova-fn) (j/get result :point))
        ;; (println "inside circle: " (inside-circle? (j/get char-pos :x) (j/get char-pos :z) x z 1.8))
        (j/assoc! state :target-pos (pc/setv (j/get state :target-pos) x y z)
                  :target-pos-available? true)
        (pc/set-locater-target x z)
        (process-running)))))

(defn- ally-selected? [e]
  (let [x (j/get e :x)
        y (j/get e :y)
        camera (j/get entity.camera/entity :camera)
        _ (pc/screen-to-world camera x y (j/get-in state [:ray :direction]))
        _ (j/call-in state [:ray :origin :copy] (pc/get-pos entity.camera/entity))
        rr-dir (j/call-in state [:ray :direction :sub] (j/get-in state [:ray :origin]))
        _ (j/call rr-dir :normalize)
        mesh (pc/find-by-name (j/get other-player :entity) "orc_warrior_mesh")
        aabb (j/get-in mesh [:render :meshInstances 0 :aabb])]
    (j/call aabb :intersectsRay (j/get state :ray) (j/get state :hit-position))))

(defn- register-mouse-events []
  (pc/on-mouse :EVENT_MOUSEDOWN
               (fn [e]
                 (when (and (pc/button? e :MOUSEBUTTON_LEFT)
                            (or (= "CANVAS" (j/get-in e [:element :nodeName]))
                                (not= "all" (-> (j/call js/window :getComputedStyle (j/get e :element))
                                                (j/get :pointerEvents)))))
                   (j/assoc! state :mouse-left-locked? true)
                   #_(when-not (ally-selected? e))
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

(defn- create-model-and-template-entity [{:keys [entity race class other-player?]}]
  (let [template-entity-name (str race "_" class)
        model-entity-name (str race "_" class "_model")
        character-template-entity (pc/clone (pc/find-by-name template-entity-name))
        character-model-entity (pc/find-by-name character-template-entity model-entity-name)]
    (j/assoc! character-template-entity :enabled true)
    (when other-player?
      (j/assoc! (pc/find-by-name character-model-entity (str race "_" class "_mesh")) :enabled false)
      (j/assoc! (pc/find-by-name character-model-entity (str race "_" class "_mesh_lod_0")) :enabled true))
    (pc/set-loc-pos character-template-entity 0 0 0)
    (pc/add-child entity character-template-entity)
    [character-template-entity character-model-entity]))

;; TODO username text elementleri faceCamera.js kullaniyor, o scripti kaldir, toplu bir sekilde yap kodda
(defn- create-username-text [{:keys [entity username race class enemy?]}]
  (let [template-entity-name (str race "_" class)
        character-template-entity (pc/find-by-name entity template-entity-name)
        username-text-entity (pc/clone (pc/find-by-name "char_name"))]
    (j/assoc-in! username-text-entity [:element :text] username)
    (j/assoc-in! username-text-entity [:element :color] username-color)
    (when enemy?
      (j/assoc-in! username-text-entity [:element :color] username-enemy-color))
    (j/assoc-in! username-text-entity [:element :outlineThickness] 1.5)
    (pc/add-child character-template-entity username-text-entity)
    (when (and (= race "orc")
               (or (= class "priest")
                   (= class "warrior")))
      (pc/set-loc-pos username-text-entity 0 0.05 0))
    (j/assoc-in! username-text-entity [:script :enabled] true)))

(defn- init-player [{:keys [id username class race mana health pos]} player]
  (let [[x y z] pos]
    (j/assoc! state
              :id id
              :username username
              :race (name race)
              :class (name class)
              :mana mana
              :health health)
    (when pos
      (j/call-in player [:rigidbody :teleport] x y z))))

(defn spawn [[x y z]]
  (j/call-in player-entity [:rigidbody :teleport] x y z))

(defn- add-skill-effects [template-entity]
  (pc/add-child template-entity (pc/clone (pc/find-by-name "damage_effects")))
  ;; add skill effect initial counters and related entities
  (->> ["asas_eyes"
        "shield"
        "portal"
        "attack_slow_down"
        "attack_r"
        "attack_flame"
        "attack_dagger"
        "attack_one_hand"
        "particle_got_defense_break"]
       (map (fn [e] [e {:counter 0
                        :entity (pc/find-by-name template-entity e)}]))
       (into {})
       clj->js))

(defn- create-throw-nova-fn [character-template-entity]
  (some-> (pc/find-by-name character-template-entity "nova") skills.mage/create-throw-nova-fn))

(defn create-player [{:keys [username class race pos] :as opts}]
  (let [enemy? (not= race (j/get state :race))
        entity-name (if enemy? "enemy_player" "ally_player")
        entity (pc/clone (pc/find-by-name entity-name))
        [x y z] pos
        params {:entity entity
                :username username
                :class class
                :race race
                :enemy? enemy?
                :other-player? true}
        _ (pc/add-child (pc/root) entity)
        [character-template-entity model-entity] (create-model-and-template-entity params)
        effects (add-skill-effects character-template-entity)
        nova-fn (create-throw-nova-fn character-template-entity)]
    (create-username-text params)
    (if enemy?
      (j/call-in entity [:rigidbody :teleport] x y z)
      (pc/set-pos entity x y z))
    (-> opts
        (dissoc :pos)
        (assoc :entity entity
               :model-entity model-entity
               :effects effects
               :enemy? enemy?
               :throw-nova-fn nova-fn)
        clj->js)))

(comment
  (pc/set-selected-char-position)
  (js/clearInterval 927)
  (js/setInterval
    (fn []
      (let [s (pc/get-pos player-entity)]
       (pc/set-selected-char-position (j/get s :x) (j/get s :z))))
    16.6)

  (let [mesh (pc/find-by-name (j/get other-player :entity) "orc_warrior_mesh")
        aabb (j/get-in mesh [:render :meshInstances 0 :aabb])]
    (js/console.log (j/get-in mesh [:render :meshInstances 0 :aabb])))


  (j/call-in player-entity [:rigidbody :teleport] 29.1888 0.55 -30.958)
  (pc/set-selected-char-position 28.410 -30.16)
  (pc/get-pos player-entity)
  (set! other-player (create-player {:id 0
                                     :username "F9Devil"
                                     :race "orc"
                                     :class "warrior"
                                     :pos [38.5690803527832 0.550000011920929 -41.28248596191406]}))

  (j/assoc! (j/get-in other-player [:entity :rigidbody]) :enabled true)
  (j/assoc! (j/get-in other-player [:entity :rigidbody]) :enabled false)
  (j/assoc! (j/get-in other-player [:entity :collision]) :enabled true)
  (j/assoc! (j/get-in other-player [:entity :collision]) :enabled false)
  (js/console.log (j/get-in other-player [:entity :rigidbody]))
  (js/console.log (j/get-in other-player [:entity]))
  (j/get-in other-player [:entity :rigidbody :enabled])

  (pc/set-anim-boolean other-player "attackOneHand" true)
  (pc/set-anim-boolean other-player "attackOneHand" false)
  (pc/set-anim-boolean other-player "attackSlowDown" true)
  (pc/set-anim-boolean other-player "attackSlowDown" false)
  (pc/set-anim-boolean other-player "attackR" true)
  (pc/set-anim-boolean other-player "attackR" false)

  (pc/set-anim-boolean other-player "run" true)
  (pc/set-anim-boolean other-player "run" false)
  (pc/set-anim-int other-player "health" 100)
  (pc/reset-anim other-player)
  (pc/play-anim other-player)

  (pc/get-anim-state other-player)
  (pc/set-anim-boolean other-player "attackOneHand" true)
  (j/assoc! other-player :enabled false)
  (j/assoc! other-player :enabled true)

  (skills.effects/apply-effect-attack-r state)
  (skills.effects/apply-effect-attack-flame state)
  (skills.effects/apply-effect-attack-dagger state)
  (skills.effects/apply-effect-attack-one-hand state)
  (skills.effects/apply-effect-attack-slow-down state)
  (skills.effects/apply-effect-attack-portal state)
  (let [p (create-player {:id 0
                          :username "F9Devil"
                          :race (first (shuffle ["human" "orc"]))
                          :class (first (shuffle ["priest" "asas" "warrior" "mage"]))
                          :pos [(+ 38 (rand 1)) 0.55 (- (+ 39 (rand 4)))]})]
    (j/assoc! ee (:id p) p))

  (j/assoc! state :runs-fast? true)
  (j/assoc! state :runs-fast? false)

  (do
    (j/assoc! state :speed 550)
    (j/assoc-in! model-entity [:anim :speed] 1))

  (j/assoc! state :speed 1750)

  (j/call-in player-entity [:rigidbody :teleport] (+ 38 (rand 1)) 0.55 (- (+ 39 (rand 4))))

  (dotimes [_ 10]
    (create-player {:username "F9Devil"
                    :race (first (shuffle ["human" "orc"]))
                    :class (first (shuffle ["priest" "asas" "warrior" "mage"]))
                    :pos [(+ 38 (rand 1)) 0.55 (- (+ 39 (rand 4)))]}))

  (pc/distance
    (pc/vec3 34.4639892578125 0.5908540487289429 -28.19676399230957)
    (pc/vec3 32.44767379760742 1.6244267225265503 26.892711639404297))

  (pc/distance (pc/vec3 8.323365211486816 0.583661675453186 32.249542236328125) (pc/get-pos player-entity))

  (j/assoc! state :throw-nova-fn (create-throw-nova-fn (j/get state :template-entity)))
  )

(defn- init-fn [this player-data]
  (let [player-entity* (j/get this :entity)
        _ (init-player player-data player-entity*)
        params {:entity player-entity*
                :username (j/get state :username)
                :race (j/get state :race)
                :class (j/get state :class)}
        [template-entity model-entity*] (create-model-and-template-entity params)]
    (create-username-text params)
    (j/assoc! state :camera (pc/find-by-name "camera"))
    (set! player-entity player-entity*)
    (set! model-entity model-entity*)
    (set! skills/model-entity model-entity*)
    (j/assoc! state
              :effects (add-skill-effects template-entity)
              :throw-nova-fn (create-throw-nova-fn template-entity)
              :template-entity template-entity)
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
            (pc/set-anim-boolean model-entity "run" true)))))
    #_(let [{:keys [x z]} (pc/get-pos player-entity)]
        (pc/set-selected-char-position x z))))

(defn get-position []
  (pc/get-pos player-entity))

(defn get-state []
  (pc/get-anim-state model-entity))

(defn- update-fn [dt this]
  (process-movement dt this))

(defn init [player-data]
  (pc/create-script :player
                    {:init (fnt (init-fn this player-data))
                     :update (fnt (update-fn dt this))
                     :post-init (fnt (when-not dev?
                                       (j/assoc! player-entity :name (str (random-uuid)))))}))

(defn enable-effect [name]
  (j/assoc! (pc/find-by-name player-entity name) :enabled true))

(defn disable-effect [name]
  (j/assoc! (pc/find-by-name player-entity name) :enabled false))
