(ns enion-cljs.scene.entities.player
  (:require
    [applied-science.js-interop :as j]
    [enion-cljs.common :as common :refer [dev? fire on]]
    [enion-cljs.scene.entities.camera :as entity.camera]
    [enion-cljs.scene.keyboard :as k]
    [enion-cljs.scene.pc :as pc]
    [enion-cljs.scene.skills.asas :as skills.asas]
    [enion-cljs.scene.skills.core :as skills]
    [enion-cljs.scene.skills.effects :as skills.effects]
    [enion-cljs.scene.skills.mage :as skills.mage]
    [enion-cljs.scene.skills.priest :as skills.priest]
    [enion-cljs.scene.skills.warrior :as skills.warrior]
    [enion-cljs.scene.states :as st :refer [player other-players]])
  (:require-macros
    [enion-cljs.scene.macros :refer [fnt]]))

(when dev?
  (defonce player-default player))

(def username-color (pc/color 2 2 2))
(def username-party-color (pc/color 2 2 0))
(def username-enemy-color (pc/color 2 0 0))

(def char-selection-distance-threshold 35)

(defn- pressing-wasd-or-has-target? []
  (or (k/pressing-wasd?) (j/get player :target-pos-available?)))

(defn- process-running []
  (if (pressing-wasd-or-has-target?)
    (pc/set-anim-boolean (st/get-model-entity) "run" true)
    (pc/set-anim-boolean (st/get-model-entity) "run" false)))

(defn- process-esc [e]
  (when (= "Escape" (j/get-in e [:event :key]))
    (when (j/get player :selected-player-id)
      (st/cancel-selected-player))))

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

(defn- get-selected-ally-id [e]
  (let [x (j/get e :x)
        y (j/get e :y)
        camera (j/get entity.camera/entity :camera)
        _ (pc/screen-to-world camera x y (j/get-in player [:ray :direction]))
        _ (j/call-in player [:ray :origin :copy] (pc/get-pos entity.camera/entity))
        rr-dir (j/call-in player [:ray :direction :sub] (j/get-in player [:ray :origin]))
        _ (j/call rr-dir :normalize)]
    (some
      (fn [id]
        (when-not (j/get-in other-players [id :enemy?])
          (let [ally (j/get other-players id)
                race (j/get ally :race)
                class (j/get ally :class)
                model-entity (j/get ally :model-entity)
                mesh-names [(str race "_" class "_mesh_lod_0")
                            (str race "_" class "_mesh_lod_1")
                            (str race "_" class "_mesh_lod_2")]
                mesh (some (fn [m]
                             (let [e (pc/find-by-name model-entity m)]
                               (j/get e :enabled)
                               e)) mesh-names)
                aabb (j/get-in mesh [:render :meshInstances 0 :aabb])
                hit? (j/call aabb :intersectsRay (j/get player :ray) (j/get player :hit-position))]
            (when hit?
              id))))
      (js/Object.keys other-players))))

(defn- get-position []
  (pc/get-pos (st/get-player-entity)))

;; TODO also update UI as well
(defn- show-player-selection-circle []
  (if-let [selected-player-id (j/get player :selected-player-id)]
    (if-let [other-player (j/get other-players selected-player-id)]
      (let [e (j/get other-player :entity)
            pos (pc/get-pos e)
            distance (pc/distance pos (get-position))]
        (cond
          (< distance char-selection-distance-threshold)
          (pc/set-selected-player-position (j/get pos :x) (j/get pos :z))

          (and (j/get other-player :enemy?) (> distance char-selection-distance-threshold))
          (st/cancel-selected-player)

          (and (not (j/get other-player :enemy?)) (> distance char-selection-distance-threshold))
          (pc/set-selected-player-position)))
      (st/cancel-selected-player))
    (st/cancel-selected-player)))

(defn get-state []
  (pc/get-anim-state (st/get-model-entity)))

(defn- has-phantom-vision? []
  (j/get player :phantom-vision?))

(defn- get-hidden-enemy-asases []
  (reduce
    (fn [acc id]
      (let [other-player (j/get other-players id)]
        (if (and (j/get other-player :enemy?) (j/get other-player :hide?))
          (conj acc other-player)
          acc)))
    [] (js/Object.keys other-players)))

(defn- select-closest-enemy* []
  (if-let [id (->> (js/Object.keys other-players)
                   (map
                     (fn [id]
                       (let [player (st/get-other-player id)
                             player-pos (pc/get-pos (j/get player :entity))
                             distance (pc/distance (get-position) player-pos)
                             ally? (not (j/get player :enemy?))
                             hide? (j/get player :hide?)
                             health (j/get player :health)]
                         [id distance ally? hide? health])))
                   (remove
                     (fn [[_ distance ally? hide? health]]
                       (or ally? hide? (> distance char-selection-distance-threshold) (zero? health))))
                   (sort-by second)
                   ffirst)]
    (do
      (pc/set-selected-char-color false)
      (st/set-selected-player id))
    (st/cancel-selected-player)))

;; TODO chat acikken de oluyor, fix it
(defn- select-closest-enemy [e]
  (when (and (st/alive?) (= "KeyZ" (j/get-in e [:event :code])))
    (select-closest-enemy*)))

(defn- look-at-selected-player [e]
  (when (and (st/alive?) (= "KeyX" (j/get-in e [:event :code])))
    (when-let [selected-player (some-> (st/get-selected-player-id) (st/get-other-player-entity))]
      (let [selected-player-pos (pc/get-pos selected-player)
            x (j/get selected-player-pos :x)
            y (j/get selected-player-pos :y)
            z (j/get selected-player-pos :z)
            model-entity (st/get-model-entity)
            char-pos (pc/get-pos model-entity)]
        (pc/look-at model-entity x (j/get char-pos :y) z true)
        (j/assoc! player
                  :target-pos (pc/setv (j/get player :target-pos) x y z)
                  :target-pos-available? true)))))

(defn- register-keyboard-events []
  (let [class (j/get player :class)
        [process-skills events] (case class
                                  "warrior" [skills.warrior/process-skills skills.warrior/events]
                                  "asas" [skills.asas/process-skills skills.asas/events]
                                  "priest" [skills.priest/process-skills skills.priest/events]
                                  "mage" [skills.mage/process-skills skills.mage/events])]
    (skills/register-key->skills (common/skill-slot-order-by-class (keyword class)))
    (pc/on-keyboard :EVENT_KEYDOWN
                    (fn [e]
                      ;; TODO chat acikken burayi disable edelim
                      (process-esc e)
                      (process-skills e)
                      (select-closest-enemy e)
                      (look-at-selected-player e)))
    (pc/on-keyboard :EVENT_KEYUP
                    (fn [e]
                      (process-running)))
    (skills/register-skill-events events)
    (on :update-skills-order skills/register-key->skills)))

(defn enable-phantom-vision []
  (j/assoc! player :phantom-vision? true)
  (doseq [a (get-hidden-enemy-asases)]
    (j/call-in a [:skills :hide])))

(defn disable-phantom-vision []
  (j/assoc! player :phantom-vision? false)
  (doseq [a (get-hidden-enemy-asases)]
    (j/call-in a [:skills :hide])))

(defn- get-selected-enemy-id [e]
  (let [result (pc/raycast-rigid-body e entity.camera/entity)
        hit-entity-name (j/get-in result [:entity :name])]
    (when (= "enemy_player" hit-entity-name)
      (let [enemy-id (j/get-in result [:entity :id])
            enemy (st/get-other-player enemy-id)
            enemy-hidden? (j/get enemy :hide?)]
        (when (or (not enemy-hidden?)
                  (and enemy-hidden? (has-phantom-vision?)))
          (str enemy-id))))))

(defn- set-target-position [e]
  (let [result (pc/raycast-rigid-body e entity.camera/entity)
        hit-entity-name (j/get-in result [:entity :name])]
    (when (= "terrain" hit-entity-name)
      (let [x (j/get-in result [:point :x])
            y (j/get-in result [:point :y])
            z (j/get-in result [:point :z])
            model-entity (st/get-model-entity)
            char-pos (pc/get-pos model-entity)]
        (when (and (not (skills/char-cant-run?)) (st/alive?))
          (pc/look-at model-entity x (j/get char-pos :y) z true))
        (j/assoc! player :target-pos (pc/setv (j/get player :target-pos) x y z)
                  :target-pos-available? true)
        (pc/set-locater-target x z)
        (process-running)))))

(defn- select-player-or-set-target [e]
  (if-let [ally-id (get-selected-ally-id e)]
    (do
      (pc/set-selected-char-color true)
      (st/set-selected-player ally-id))
    (if-let [enemy-id (get-selected-enemy-id e)]
      (do
        (pc/set-selected-char-color false)
        (st/set-selected-player enemy-id))
      (set-target-position e))))

(defn- show-nova-circle [e]
  (when (j/get player :positioning-nova?)
    (let [result (pc/raycast-rigid-body e entity.camera/entity)
          hit-entity-name (j/get-in result [:entity :name])]
      (when (= "terrain" hit-entity-name)
        (let [x (j/get-in result [:point :x])
              y (j/get-in result [:point :y])
              z (j/get-in result [:point :z])]
          ;; (inside-circle? (j/get char-pos :x) (j/get char-pos :z) x z 2.25)
          (pc/set-nova-circle-pos player x y z))))))

(defn- register-mouse-events []
  (pc/on-mouse :EVENT_MOUSEDOWN
               (fn [e]
                 (when (and (pc/button? e :MOUSEBUTTON_LEFT)
                            (or (= "CANVAS" (j/get-in e [:element :nodeName]))
                                (not= "all" (-> (j/call js/window :getComputedStyle (j/get e :element))
                                                (j/get :pointerEvents))))
                            (not (j/get player :positioning-nova?)))
                   (j/assoc! player :mouse-left-locked? true)
                   (select-player-or-set-target e))))

  (when (st/mage?)
    (pc/on-mouse :EVENT_MOUSEDOWN
                 (fn [e]
                   (when (pc/button? e :MOUSEBUTTON_LEFT)
                     (skills.mage/throw-nova e))))
    (pc/on-mouse :EVENT_MOUSEMOVE
                 (fn [e]
                   (when-not (j/get player :mouse-left-locked?)
                     (show-nova-circle e)))))

  (pc/on-mouse :EVENT_MOUSEUP
               (fn [e]
                 (when (pc/button? e :MOUSEBUTTON_LEFT)
                   (j/assoc! player :mouse-left-locked? false))))

  (pc/on-mouse :EVENT_MOUSEMOVE
               (fn [e]
                 (when (j/get player :mouse-left-locked?)
                   (set-target-position e)))))

(defn- collision-start [result]
  (when (= "terrain" (j/get-in result [:other :name]))
    (j/assoc! player :on-ground? true)))

(defn- collision-end [result]
  (when (= "terrain" (j/get result :name))
    (j/assoc! player :on-ground? false)))

(defn- register-collision-events [entity]
  (j/call-in entity [:collision :on] "collisionstart" collision-start)
  (j/call-in entity [:collision :on] "collisionend" collision-end))

(defn- create-model-and-template-entity [{:keys [id entity race class other-player? enemy?]}]
  (let [template-entity-name (str race "_" class)
        model-entity-name (str race "_" class "_model")
        character-template-entity (pc/clone (pc/find-by-name template-entity-name))
        _ (j/assoc! character-template-entity :name (str template-entity-name "_" id))
        character-model-entity (pc/find-by-name character-template-entity model-entity-name)
        model-y-offset (js/Math.abs (j/get (pc/get-loc-pos character-model-entity) :y))]
    (j/assoc! character-template-entity :enabled true)
    (when other-player?
      (j/assoc! (pc/find-by-name character-model-entity (str race "_" class "_mesh")) :enabled false)
      (j/assoc! (pc/find-by-name character-model-entity (str race "_" class "_mesh_lod_0")) :enabled true))
    (pc/set-loc-pos character-template-entity 0 0 0)
    (when other-player?
      (pc/set-loc-pos character-model-entity 0 0 0))
    (pc/add-child entity character-template-entity)
    [character-template-entity character-model-entity model-y-offset]))

;; TODO username text elementleri faceCamera.js kullaniyor, o scripti kaldir, toplu bir sekilde yap kodda
(defn- create-username-text [{:keys [template-entity username race class other-player? enemy? model-y-offset]}]
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
      (when other-player?
        (pc/set-loc-pos username-text-entity 0 0.05 0)))
    (when model-y-offset
      (let [{:keys [x y z]} (j/lookup (pc/get-loc-pos username-text-entity))]
        (pc/set-loc-pos username-text-entity x (+ y model-y-offset) z)))
    (j/assoc-in! username-text-entity [:script :enabled] true)))

(defn- init-player [{:keys [id username class race mana health pos]} player-entity]
  (let [[x y z] pos]
    (j/assoc! player
              :id id
              :username username
              :race (name race)
              :class (name class)
              :health health
              :total-health health
              :mana mana
              :total-mana mana
              :heal-counter 0)
    (when pos
      (j/call-in player-entity [:rigidbody :teleport] x y z))
    (fire :ui-player-set-total-health-and-mana {:health health
                                                :mana mana})))

(defn spawn [[x y z]]
  (j/call-in (st/get-player-entity) [:rigidbody :teleport] x y z))

(defn- add-skill-effects
  ([template-entity]
   (add-skill-effects template-entity nil))
  ([template-entity model-y-offset]
   (let [effects (pc/clone (pc/find-by-name "effects"))]
     (pc/add-child template-entity effects)
     (when model-y-offset
       (let [{:keys [x y z]} (j/lookup (pc/get-loc-pos effects))]
         (pc/set-loc-pos effects x (+ y model-y-offset) z)))
     ;; add skill effect initial counters and related entities
     (->> (map (j/get :name) (j/get effects :children))
          (concat
            ["particle_fire_hands"
             "particle_flame_dots"
             "particle_heal_hands"
             "particle_cure_hands"
             "particle_defense_break_hands"])
          (keep
            (fn [e]
              (when-let [entity (pc/find-by-name template-entity e)]
                [e {:counter 0
                    :entity entity
                    :state #js {:value 0}}])))
          (into {})
          clj->js))))

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

(defn move-player [x y z]
  (j/call-in (st/get-player-entity) [:rigidbody :teleport] x y z))

(comment
  (js/clearInterval 1319)
  player
  (disable-phantom-vision)
  (enable-phantom-vision)
  (move-player 0 3 0)
  (skills.effects/apply-effect-attack-r player)
  (skills.effects/apply-effect-attack-flame player)
  (skills.effects/apply-effect-attack-dagger player)
  (skills.effects/apply-effect-attack-one-hand player)
  (skills.effects/apply-effect-attack-slow-down player)
  (skills.effects/apply-effect-attack-portal player)
  (skills.effects/apply-effect-got-defense-break player)
  (skills.effects/apply-effect-fire-hands player)
  (j/call-in player-entity [:rigidbody :teleport] (+ 38 (rand 1)) 0.55 (- (+ 39 (rand 4))))

  (j/assoc! player :fleet-foot? true)
  (j/assoc! player :speed 550)
  (set-speed 550)
  (set-speed 1750)
  )

(defn set-speed [velocity]
  (j/assoc! player :speed velocity))

(defn- create-asas-skill-fns [state other-player?]
  (if other-player?
    (-> state
        (j/assoc-in! [:skills :hide] (skills.asas/create-hide-fn-other-player state))
        (j/assoc-in! [:skills :appear] (skills.asas/create-appear-fn-other-player state)))
    (-> state
        (j/assoc-in! [:skills :hide] (skills.asas/create-hide-fn))
        (j/assoc-in! [:skills :appear] (skills.asas/create-appear-fn)))))

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
  (when-not id
    (throw (ex-info "Id does not exist for player!" {})))
  (if (j/get st/other-players id)
    (js/console.warn "Player with this ID already exists!")
    (let [enemy? (not= race (j/get player :race))
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
          [template-entity model-entity model-y-offset] (create-model-and-template-entity params)
          effects (add-skill-effects template-entity model-y-offset)
          state (-> opts
                    (dissoc :pos)
                    (assoc :entity entity
                           :model-entity model-entity
                           :template-entity template-entity
                           :effects effects
                           :enemy? enemy?
                           :health health
                           :total-health health
                           :mana mana
                           :total-mana mana
                           :heal-counter 0
                           :tween {:interpolation nil
                                   :initial-pos #js {}
                                   :last-pos #js {}})
                    clj->js)]
      (create-username-text (assoc params :template-entity template-entity
                                   :model-y-offset model-y-offset))
      (create-skill-fns state true)
      (if enemy?
        (j/call-in entity [:rigidbody :teleport] x y z)
        (pc/set-pos entity x y z))
      state)))

(defn- update-fleet-foot-cooldown-if-asas [class]
  (when (= "asas" class)
    (common/update-skill-cooldown "fleetFoot" 13000)))

(defn- init-fn [this player-data]
  (let [player-entity (j/get this :entity)
        _ (init-player player-data player-entity)
        opts {:id (:id player-data)
              :entity player-entity
              :username (j/get player :username)
              :race (j/get player :race)
              :class (j/get player :class)}
        [template-entity model-entity] (create-model-and-template-entity opts)]
    (create-username-text (assoc opts :template-entity template-entity))
    (j/assoc! player :camera (pc/find-by-name "camera"))
    (j/assoc! player
              :this this
              :effects (add-skill-effects template-entity)
              :template-entity template-entity
              :model-entity model-entity
              :entity player-entity)
    (create-skill-fns player)
    (register-keyboard-events)
    (register-mouse-events)
    (register-collision-events player-entity)
    (update-fleet-foot-cooldown-if-asas (j/get player :class))
    (fire :init-skills (keyword (j/get player :class)))
    (on :create-players (fn [players]
                          (doseq [p players]
                            (st/add-player (create-player p)))))
    (on :cooldown-ready? (fn [{:keys [ready? skill]}]
                           (st/set-cooldown ready? skill)))))

;; TODO add if entity is able to move - like app-focused? and alive? etc.
(defn- process-movement [dt _]
  (when (st/alive?)
    (let [speed (j/get player :speed)
          camera (j/get player :camera)
          right (j/get camera :right)
          forward (j/get camera :forward)
          world-dir (j/get player :world-dir)
          temp-dir (j/get player :temp-dir)
          player-entity (st/get-player-entity)
          model-entity (st/get-model-entity)]
      (when-not (skills/char-cant-run?)
        (if (j/get player :target-pos-available?)
          (let [target (j/get player :target-pos)
                temp-dir (pc/copyv temp-dir target)
                pos (get-position)
                dir (-> temp-dir (pc/sub pos) pc/normalize (pc/scale speed))]
            (if (>= (pc/distance target pos) 0.2)
              (pc/apply-force player-entity (j/get dir :x) 0 (j/get dir :z))
              (do
                (j/assoc! player :target-pos-available? false)
                (pc/set-locater-target)
                (process-running))))
          (do
            (pc/setv world-dir 0 0 0)
            (j/assoc! player :x 0 :z 0 :target-y (j/get-in entity.camera/state [:eulers :x]))
            (when (pc/pressed? :KEY_W)
              (j/update! player :z inc))
            (when (pc/pressed? :KEY_A)
              (j/update! player :x dec))
            (when (pc/pressed? :KEY_S)
              (j/update! player :z dec))
            (when (pc/pressed? :KEY_D)
              (j/update! player :x inc))

            (when (or (not= (j/get player :x) 0)
                      (not= (j/get player :z) 0))
              (pc/addv world-dir (pc/mul-scalar (pc/copyv temp-dir forward) (j/get player :z)))
              (pc/addv world-dir (pc/mul-scalar (pc/copyv temp-dir right) (j/get player :x)))
              (-> world-dir pc/normalize (pc/scale speed))
              (pc/apply-force player-entity (j/get world-dir :x) 0 (j/get world-dir :z)))

            (cond
              (and (pc/pressed? :KEY_A) (pc/pressed? :KEY_W)) (j/update! player :target-y + 45)
              (and (pc/pressed? :KEY_D) (pc/pressed? :KEY_W)) (j/update! player :target-y - 45)
              (and (pc/pressed? :KEY_A) (pc/pressed? :KEY_S)) (j/update! player :target-y + 135)
              (and (pc/pressed? :KEY_D) (pc/pressed? :KEY_S)) (j/update! player :target-y - 135)
              (pc/pressed? :KEY_A) (j/update! player :target-y + 90)
              (pc/pressed? :KEY_D) (j/update! player :target-y - 90)
              (pc/pressed? :KEY_S) (j/update! player :target-y + 180))
            (when (pressing-wasd-or-has-target?)
              (pc/set-loc-euler model-entity 0 (j/get player :target-y) 0)
              (pc/set-anim-boolean model-entity "run" true))))
        (when (and (= "run" (pc/get-anim-state model-entity)) (j/get player :on-ground?))
          (when (> (j/get player :sound-run-elapsed-time) (if (j/get player :fleet-foot?) 0.3 0.4))
            (if (j/get player :sound-run-1?)
              (do
                (j/call-in (st/get-player-entity) [:c :sound :slots "run_1" :play])
                (j/assoc! player :sound-run-1? false))
              (do
                (j/call-in (st/get-player-entity) [:c :sound :slots "run_2" :play])
                (j/assoc! player :sound-run-1? true)))
            (j/assoc! player :sound-run-elapsed-time 0))
          (j/update! player :sound-run-elapsed-time + dt))))))

(defn enable-effect [name]
  (j/assoc! (pc/find-by-name (st/get-player-entity) name) :enabled true))

(defn disable-effect [name]
  (j/assoc! (pc/find-by-name (st/get-player-entity) name) :enabled false))

(defn- update-fn [dt this]
  (process-movement dt this)
  (show-player-selection-circle))

(defn re-init-player [{:keys [id username race class]}]
  (let [this (j/get player :this)]
    (j/call-in player [:template-entity :destroy])
    (set! player player-default)
    (init-fn this {:id id
                   :username username
                   :race race
                   :class class
                   :mana 100
                   :health 100
                   ;; :pos [(+ 38 (rand 1)) 0.55 (- (+ 39 (rand 4)))]
                   })))

(defn init [player-data]
  (pc/create-script :player
                    {:init (fnt (init-fn this player-data))
                     :update (fnt (update-fn dt this))
                     :post-init (fnt
                                  (when-not dev?
                                    (j/assoc! (st/get-player-entity) :name (str (random-uuid))))
                                  (fire :start-lod-manager))}))

(on :init (fn [player-data]
            (init player-data)
            (fire :connect-to-world-state)))

(when dev?
  (on :re-init (fn []
                 (re-init-player {:id 0
                                  :username "0000000"
                                  :race "orc"
                                  :class "warrior"
                                  :mana 100
                                  :health 100}))))

(comment
  (js/console.log player)
  (init {:id 0
         :username "0000000"
         :race "orc"
         :class "mage"
         :mana 100
         :health 100})
  (js/console.log (j/get-in (st/get-player-entity) [:c :sound :slots "run_1"]))


  (do

    )
  (j/call-in (st/get-player-entity) [:c :sound :slots "Slot 1" :play])
  (j/call-in (st/get-player-entity) [:c :sound :slots "Slot 2" :play])
  (j/call-in (st/get-player-entity) [:c :sound :slots "Slot 3" :play])
  (j/call-in (st/get-player-entity) [:c :sound :slots "Slot 4" :play])


  (pc/enable (j/get-in player [:effects :attack_r :entity]))
  (let [[player player2 p3] [(create-player {:id 1
                                             :username "0000000"
                                             :race "human"
                                             :class "asas"
                                             ;:pos [39.0690803527832 0.550000011920929 -42.08248596191406]
                                             :pos [(+ 38 (rand 1)) 0.55 (- (+ 39 (rand 4)))]
                                             })
                             (create-player {:id 2
                                             :username "Gandalf"
                                             :race "human"
                                             :class "mage"
                                             ;:pos [39.0690803527832 0.550000011920929 -42.08248596191406]
                                             :pos [(+ 38 (rand 1)) 0.55 (- (+ 39 (rand 4)))]
                                             })
                             (create-player {:id 3
                                             :username "Orc_Warrior"
                                             :race "orc"
                                             :class "asas"
                                             ;:pos [39.0690803527832 0.550000011920929 -42.08248596191406]
                                             :pos [(+ 38 (rand 1)) 0.55 (- (+ 39 (rand 4)))]
                                             })]]
    (j/assoc! other-players (j/get player :id) player)
    (j/assoc! other-players (j/get player2 :id) player2)
    (j/assoc! other-players (j/get p3 :id) p3)

    (set! other-players other-players))

  (st/destroy-players)
  (j/assoc! player :phantom-vision? false)
  (j/assoc! player :phantom-vision? true)

  (create-skill-fns)

  (j/call-in other-players [1 :entity :rigidbody :teleport] (+ 38 (rand 1)) 0.55 (- (+ 39 (rand 4))))
  (j/call-in other-players [2 :entity :setPosition] (+ 38 (rand 1)) 0.55 (- (+ 39 (rand 4))))

  (skills.effects/apply-effect-attack-flame (j/get other-players 1))
  (skills.effects/apply-effect-attack-dagger (j/get other-players 1))
  (move-player (j/get-in other-players [1 :entity]))

  (j/call-in player [:skills :hide])
  (j/call-in player [:skills :appear])
  (j/get-in player [:skills :hide])
  )
