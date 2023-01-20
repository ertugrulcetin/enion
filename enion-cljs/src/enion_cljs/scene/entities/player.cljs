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

(when dev?
  (defonce state-default state))

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

(defn- process-esc [e]
  (when (= "Escape" (j/get-in e [:event :key]))
    (when (j/get state :selected-char-id)
      (j/assoc! state :selected-char-id nil)
      (pc/set-selected-char-position))))

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
                      (process-esc e)
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
(defonce players #js {})

(defn- get-selected-ally-id [e]
  (let [x (j/get e :x)
        y (j/get e :y)
        camera (j/get entity.camera/entity :camera)
        _ (pc/screen-to-world camera x y (j/get-in state [:ray :direction]))
        _ (j/call-in state [:ray :origin :copy] (pc/get-pos entity.camera/entity))
        rr-dir (j/call-in state [:ray :direction :sub] (j/get-in state [:ray :origin]))
        _ (j/call rr-dir :normalize)]
    (some
      (fn [id]
        (when-not (j/get-in players [id :enemy?])
          (let [player (j/get players id)
                race (j/get player :race)
                class (j/get player :class)
                model-entity (j/get player :model-entity)
                mesh-names [(str race "_" class "_mesh_lod_0")
                            (str race "_" class "_mesh_lod_1")
                            (str race "_" class "_mesh_lod_2")]
                mesh (some (fn [m]
                             (let [e (pc/find-by-name model-entity m)]
                               (j/get e :enabled)
                               e)) mesh-names)
                aabb (j/get-in mesh [:render :meshInstances 0 :aabb])
                hit? (j/call aabb :intersectsRay (j/get state :ray) (j/get state :hit-position))]
            (when hit?
              id))))
      (js/Object.keys players))))

(defn- raycast-rigid-body [e]
  (let [x (j/get e :x)
        y (j/get e :y)
        camera (j/get entity.camera/entity :camera)
        from (pc/get-pos entity.camera/entity)
        to (pc/screen-to-world camera x y)]
    (pc/raycast-first from to)))

(defn- get-selected-enemy-id [e]
  (let [result (raycast-rigid-body e)
        hit-entity-name (j/get-in result [:entity :name])]
    (when (= "enemy_player" hit-entity-name)
      (j/get-in result [:entity :id]))))

(defn- set-target-position [e]
  (let [result (raycast-rigid-body e)
        hit-entity-name (j/get-in result [:entity :name])]
    (when (= "terrain" hit-entity-name)
      (let [x (j/get-in result [:point :x])
            y (j/get-in result [:point :y])
            z (j/get-in result [:point :z])
            char-pos (pc/get-pos model-entity)]
        (when-not (skills/char-cant-run?)
          (pc/look-at model-entity x (j/get char-pos :y) z true))
        (when (= "mage" (j/get state :class))
          ((j/get state :throw-nova-fn) (j/get result :point)))
        ;; (println "inside circle: " (inside-circle? (j/get char-pos :x) (j/get char-pos :z) x z 1.8))
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
                   (if-let [ally-id (get-selected-ally-id e)]
                     (do
                       (pc/set-selected-char-color true)
                       (j/assoc! state :selected-char-id ally-id))
                     (if-let [enemy-id (get-selected-enemy-id e)]
                       (do
                         (pc/set-selected-char-color false)
                         (j/assoc! state :selected-char-id enemy-id))
                       (set-target-position e))))))
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

(defn- create-model-and-template-entity [{:keys [id entity race class other-player?]}]
  (let [template-entity-name (str race "_" class)
        model-entity-name (str race "_" class "_model")
        character-template-entity (pc/clone (pc/find-by-name template-entity-name))
        _ (j/assoc! character-template-entity :name (str template-entity-name "_" id))
        character-model-entity (pc/find-by-name character-template-entity model-entity-name)]
    (j/assoc! character-template-entity :enabled true)
    (when other-player?
      (j/assoc! (pc/find-by-name character-model-entity (str race "_" class "_mesh")) :enabled false)
      (j/assoc! (pc/find-by-name character-model-entity (str race "_" class "_mesh_lod_0")) :enabled true))
    (pc/set-loc-pos character-template-entity 0 0 0)
    (pc/add-child entity character-template-entity)
    [character-template-entity character-model-entity]))

;; TODO username text elementleri faceCamera.js kullaniyor, o scripti kaldir, toplu bir sekilde yap kodda
(defn- create-username-text [{:keys [template-entity username race class enemy?]}]
  (let [username-text-entity (pc/clone (pc/find-by-name "char_name"))]
    (j/assoc-in! username-text-entity [:element :text] username)
    (j/assoc-in! username-text-entity [:element :color] username-color)
    (when enemy?
      (j/assoc-in! username-text-entity [:element :color] username-enemy-color))
    (j/assoc-in! username-text-entity [:element :outlineThickness] 1.5)
    (pc/add-child template-entity username-text-entity)
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
              :health health
              :heal-counter 0)
    (when pos
      (j/call-in player [:rigidbody :teleport] x y z))))

(defn spawn [[x y z]]
  (j/call-in player-entity [:rigidbody :teleport] x y z))

(defn- add-skill-effects [template-entity]
  (pc/add-child template-entity (pc/clone (pc/find-by-name "effects")))
  ;; add skill effect initial counters and related entities
  (->> ["asas_eyes"
        "shield"
        "portal"
        "attack_slow_down"
        "attack_r"
        "attack_flame"
        "attack_dagger"
        "attack_one_hand"
        "particle_got_defense_break"
        "particle_fire_hands"
        "particle_flame_dots"
        "particle_heal_hands"
        "particle_cure_hands"
        "particle_defense_break_hands"]
       (keep
         (fn [e]
           (when-let [entity (pc/find-by-name template-entity e)]
             [e {:counter 0
                 :entity entity}])))
       (into {})
       clj->js))

(defn- create-throw-nova-fn [character-template-entity]
  (some-> (pc/find-by-name character-template-entity "nova") skills.mage/create-throw-nova-fn))

#_(defn- move-player [entity]
    (let [{:keys [x y z]} (j/lookup (pc/get-pos entity))
          temp-first-pos #js {:x x :y y :z z}
          temp-final-pos #js {:x (+ 38 (rand 1))
                              :y 0.55
                              :z (- (+ 39 (rand 4)))}
          tween-pos (-> (j/call entity :tween temp-first-pos)
                      (j/call :to temp-final-pos 0.3 js/pc.Linear))
          _ (j/call tween-pos :on "update"
              (fn []
                (j/call-in entity [:rigidbody :teleport]
                  (j/get temp-first-pos :x)
                  (j/get temp-first-pos :y)
                  (j/get temp-first-pos :z))))]
      (j/call tween-pos :start)
      nil))

(comment
  (skills.effects/apply-effect-attack-r state)
  (skills.effects/apply-effect-attack-flame state)
  (skills.effects/apply-effect-attack-dagger state)
  (skills.effects/apply-effect-attack-one-hand state)
  (skills.effects/apply-effect-attack-slow-down state)
  (skills.effects/apply-effect-attack-portal state)
  (skills.effects/apply-effect-got-defense-break state)
  (skills.effects/apply-effect-fire-hands state)
  (j/call-in player-entity [:rigidbody :teleport] (+ 38 (rand 1)) 0.55 (- (+ 39 (rand 4))))

  (j/assoc! state :runs-fast? true)
  (j/assoc! state :speed 550)

  )

(defn- create-asas-skill-fns [state other-player?]
  (if other-player?
    (-> state
        (j/assoc-in! [:skills :hide] (skills.asas/create-hide-fn-other-player state))
        (j/assoc-in! [:skills :appear] (skills.asas/create-appear-fn-other-player state)))
    (-> state
        (j/assoc-in! [:skills :hide] (skills.asas/create-hide-fn state))
        (j/assoc-in! [:skills :appear] (skills.asas/create-appear-fn state)))))

(defn- create-skill-fns
  ([state]
   (create-skill-fns state false))
  ([state other-player?]
   (let [{:keys [class template-entity]} (j/lookup state)]
     (case class
       "mage" (j/assoc-in! state [:skills :throw-nova] (create-throw-nova-fn template-entity))
       "asas" (create-asas-skill-fns state other-player?)
       nil))))

(defn create-player [{:keys [id username class race pos health mana] :as opts}]
  (let [enemy? (not= race (j/get state :race))
        entity-name (if enemy? "enemy_player" "ally_player")
        ;; TODO belki ana kullanilan entityleri bir kere fetch edip cachleyip oradan kullaniriz yoksa karisiklik olabilir
        ;; ayni isimden dolayi, bir kere template entitye oldu 2 kere username sprite'i olusmustu
        entity (pc/clone (pc/find-by-name entity-name))
        _ (j/assoc! entity :id id)
        [x y z] pos
        params {:entity entity
                :username username
                :class class
                :race race
                :enemy? enemy?
                :other-player? true}
        _ (pc/add-child (pc/root) entity)
        [template-entity model-entity] (create-model-and-template-entity params)
        effects (add-skill-effects template-entity)
        state (-> opts
                  (dissoc :pos)
                  (assoc :entity entity
                         :model-entity model-entity
                         :template-entity template-entity
                         :effects effects
                         :enemy? enemy?
                         :health health
                         :mana mana
                         :heal-counter 0)
                  clj->js)]
    (create-username-text (assoc params :template-entity template-entity))
    (create-skill-fns state true)
    (if enemy?
      (j/call-in entity [:rigidbody :teleport] x y z)
      (pc/set-pos entity x y z))
    state))

(defn- init-fn [this player-data]
  (let [player-entity* (j/get this :entity)
        _ (init-player player-data player-entity*)
        opts {:id (:id player-data)
              :entity player-entity*
              :username (j/get state :username)
              :race (j/get state :race)
              :class (j/get state :class)}
        [template-entity model-entity*] (create-model-and-template-entity opts)]
    (create-username-text (assoc opts :template-entity template-entity))
    (j/assoc! state :camera (pc/find-by-name "camera"))
    (set! player-entity player-entity*)
    (set! model-entity model-entity*)
    (set! skills/model-entity model-entity*)
    (set! skills/state state)
    (j/assoc! state
              :this this
              :effects (add-skill-effects template-entity)
              :template-entity template-entity
              :model-entity model-entity*
              :entity player-entity*)
    (create-skill-fns state)
    (common/fire :init-skills (keyword (j/get state :class)))
    (skills.effects/register-player state)
    (register-keyboard-events)
    (register-mouse-events)
    (register-collision-events player-entity*))

  (let [[player player2] [(create-player {:id 1
                                          :username "Human_Warrior"
                                          :race "human"
                                          :class "asas"
                                          ;; :pos [39.0690803527832 0.550000011920929 -42.08248596191406]
                                          :pos [(+ 38 (rand 1)) 0.55 (- (+ 39 (rand 4)))]})
                          (create-player {:id 2
                                          :username "Orc_Warrior"
                                          :race "orc"
                                          :class "asas"
                                          ;; :pos [39.0690803527832 0.550000011920929 -42.08248596191406]
                                          :pos [(+ 38 (rand 1)) 0.55 (- (+ 39 (rand 4)))]})]]
    (j/assoc! players (j/get player :id) player)
    (j/assoc! players (j/get player2 :id) player2)
    (set! skills.effects/other-players players))
  (js/document.addEventListener "keydown"
                                (fn [e]
                                  (when (= (j/get e :code) "KeyH")
                                    (j/call-in skills.effects/other-players [1 :skills :hide]))
                                  (when (= (j/get e :code) "KeyJ")
                                    (j/call-in skills.effects/other-players [1 :skills :appear]))
                                  (when (= (j/get e :code) "KeyK")
                                    (j/call-in skills.effects/other-players [2 :skills :hide]))
                                  (when (= (j/get e :code) "KeyL")
                                    (j/call-in skills.effects/other-players [2 :skills :appear])))))

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

;; TODO also update UI as well
(defn- cancel-selected-char []
  (pc/set-selected-char-position)
  (j/assoc! state :selected-char-id nil))

(def char-selection-distance-threshold 35)

(defn- show-char-selection-circle []
  (when-let [selected-char-id (j/get state :selected-char-id)]
    (if-let [player (j/get players selected-char-id)]
      (let [e (j/get player :entity)
            pos (pc/get-pos e)
            distance (pc/distance pos (pc/get-pos player-entity))]
        (cond
          (< distance char-selection-distance-threshold)
          (pc/set-selected-char-position (j/get pos :x) (j/get pos :z))

          (and (j/get player :enemy?) (> distance char-selection-distance-threshold))
          (cancel-selected-char)

          (and (not (j/get player :enemy?)) (> distance char-selection-distance-threshold))
          (pc/set-selected-char-position)))
      (cancel-selected-char))))

(defn get-position []
  (pc/get-pos player-entity))

(defn get-state []
  (pc/get-anim-state model-entity))

(defn- update-fn [dt this]
  (process-movement dt this)
  (show-char-selection-circle))

(defn init [player-data]
  (pc/create-script :player
                    {:init (fnt (init-fn this player-data))
                     :update (fnt (update-fn dt this))
                     :post-init (fnt (when-not dev?
                                       (j/assoc! player-entity :name (str (random-uuid)))))}))

(comment
  (let [[player player2] [(create-player {:id 1
                                          :username "Human_Warrior"
                                          :race "human"
                                          :class "asas"
                                          ;:pos [39.0690803527832 0.550000011920929 -42.08248596191406]
                                          :pos [(+ 38 (rand 1)) 0.55 (- (+ 39 (rand 4)))]
                                          })
                          (create-player {:id 2
                                          :username "Orc_Warrior"
                                          :race "orc"
                                          :class "asas"
                                          ;:pos [39.0690803527832 0.550000011920929 -42.08248596191406]
                                          :pos [(+ 38 (rand 1)) 0.55 (- (+ 39 (rand 4)))]
                                          })]]
    (j/assoc! players (j/get player :id) player)
    (j/assoc! players (j/get player2 :id) player2)
    (set! skills.effects/other-players players))

  (create-skill-fns)

  (j/call-in skills.effects/other-players [1 :skills :hide])
  (j/call-in skills.effects/other-players [1 :skills :appear])

  (pc/set-mesh-opacity (j/get-in skills.effects/other-players [1 :model-entity :children 2]) 0.1)

  (pc/disable (j/get-in skills.effects/other-players [1 :model-entity :children 2]))
  (pc/enable (j/get-in skills.effects/other-players [1 :model-entity :children 2]))
  (js/console.log (j/get-in skills.effects/other-players [1 :model-entity :children 2]))

  (j/call-in players [1 :entity :rigidbody :teleport] (+ 38 (rand 1)) 0.55 (- (+ 39 (rand 4))))
  (j/call-in players [2 :entity :setPosition] (+ 38 (rand 1)) 0.55 (- (+ 39 (rand 4))))

  (skills.effects/apply-effect-attack-flame (j/get players 1))
  (skills.effects/apply-effect-attack-dagger (j/get players 1))
  (move-player (j/get-in players [1 :entity]))

  (j/call-in state [:skills :hide])
  (j/call-in state [:skills :appear])
  (j/get-in state [:skills :hide])

  (let [this (j/get state :this)]
    (j/call-in state [:template-entity :destroy])
    (set! state state-default)
    (init-fn this {:id 1
                   :username "0000000"
                   :race "orc"
                   :class "asas"
                   :mana 100
                   :health 100
                   ;; :pos [(+ 38 (rand 1)) 0.55 (- (+ 39 (rand 4)))]
                   })))

(defn enable-effect [name]
  (j/assoc! (pc/find-by-name player-entity name) :enabled true))

(defn disable-effect [name]
  (j/assoc! (pc/find-by-name player-entity name) :enabled false))
