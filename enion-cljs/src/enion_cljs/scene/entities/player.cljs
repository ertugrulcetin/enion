(ns enion-cljs.scene.entities.player
  (:require
    [applied-science.js-interop :as j]
    [enion-cljs.common :as common :refer [dev? fire on dlog]]
    [enion-cljs.scene.entities.base :as entity.base]
    [enion-cljs.scene.entities.camera :as entity.camera]
    [enion-cljs.scene.pc :as pc]
    [enion-cljs.scene.skills.asas :as skills.asas]
    [enion-cljs.scene.skills.core :as skills]
    [enion-cljs.scene.skills.effects :as skills.effects]
    [enion-cljs.scene.skills.mage :as skills.mage]
    [enion-cljs.scene.skills.priest :as skills.priest]
    [enion-cljs.scene.skills.warrior :as skills.warrior]
    [enion-cljs.scene.states :as st :refer [player other-players npcs]]
    [enion-cljs.scene.utils :as utils])
  (:require-macros
    [enion-cljs.scene.macros :refer [fnt]]))

(defonce player-script nil)

(def char-selection-distance-threshold 35)

(defn- process-esc [e]
  (when (= "Escape" (j/get-in e [:event :key]))
    (when (j/get player :selected-player-id)
      (st/cancel-selected-player))
    (when (j/get player :positioning-nova?)
      (j/assoc! player :positioning-nova? false)
      (pc/set-nova-circle-pos))))

(defn- square [n]
  (js/Math.pow n 2))

(defn inside-circle?
  "Clojure formula of (x - center_x)² + (y - center_y)² < radius²"
  [x z center-x center-z radius]
  (< (+ (square (- x center-x)) (square (- z center-z))) (square radius)))

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

(defn- get-selected-enemy-npc-id [e]
  (let [x (j/get e :x)
        y (j/get e :y)
        camera (j/get entity.camera/entity :camera)
        _ (pc/screen-to-world camera x y (j/get-in player [:ray :direction]))
        _ (j/call-in player [:ray :origin :copy] (pc/get-pos entity.camera/entity))
        rr-dir (j/call-in player [:ray :direction :sub] (j/get-in player [:ray :origin]))
        _ (j/call rr-dir :normalize)]
    (some
      (fn [id]
        (let [npc (st/get-npc id)
              model-entity (j/get npc :model-entity)
              prefix "skeleton_warrior"
              mesh-names [(str prefix "_mesh_lod_0")
                          (str prefix "_mesh_lod_1")
                          (str prefix "_mesh_lod_2")]
              mesh (some (fn [m]
                           (let [e (pc/find-by-name model-entity m)]
                             (j/get e :enabled)
                             e)) mesh-names)
              aabb (j/get-in mesh [:render :meshInstances 0 :aabb])
              hit? (j/call aabb :intersectsRay (j/get player :ray) (j/get player :hit-position))]
          (when (and hit? (> (j/get npc :health) 0))
            id)))
      (js/Object.keys npcs))))

(defn- get-position []
  (pc/get-pos (st/get-player-entity)))

(defn- show-player-selection-circle []
  (if-let [selected-player-id (j/get player :selected-player-id)]
    (if-let [npc-or-other-player (if (j/get player :selected-enemy-npc?)
                                   (st/get-npc selected-player-id)
                                   (st/get-other-player selected-player-id))]
      (let [e (j/get npc-or-other-player :entity)
            pos (pc/get-pos e)
            distance (pc/distance pos (get-position))]
        (cond
          (and (j/get npc-or-other-player :enemy?) (or (> distance char-selection-distance-threshold)
                                                       (and (j/get npc-or-other-player :hide?)
                                                            (not (j/get player :phantom-vision?)))))
          (st/cancel-selected-player)

          (< distance char-selection-distance-threshold)
          (pc/set-selected-player-position (j/get pos :x) (j/get pos :z))

          (and (not (j/get npc-or-other-player :enemy?)) (> distance char-selection-distance-threshold))
          (pc/set-selected-player-position)))
      (st/cancel-selected-player))
    (st/cancel-selected-player)))

(defn get-state []
  (pc/get-anim-state (st/get-model-entity)))

(let [temp #js {}]
  (on :state-for-minimap
      (fn []
        (when (st/get-player-entity)
          (let [state (get-state)
                pos (get-position)]
            (j/assoc! temp
                      :anim-state state
                      :x (j/get pos :x)
                      :z (j/get pos :z))
            (fire :state-for-minimap-response temp))))))

(defn- has-phantom-vision? []
  (j/get player :phantom-vision?))

(defn- select-closest-enemy* [npc?]
  (if-let [id (->> (js/Object.keys (if npc? npcs other-players))
                   (map
                     (fn [id]
                       (let [player-or-npc (if npc?
                                             (st/get-npc id)
                                             (st/get-other-player id))
                             player-pos (pc/get-pos (j/get player-or-npc :entity))
                             distance (pc/distance (get-position) player-pos)
                             ally? (not (j/get player-or-npc :enemy?))
                             hide? (j/get player-or-npc :hide?)
                             health (j/get player-or-npc :health)]
                         [id distance ally? hide? health])))
                   (remove
                     (fn [[_ distance ally? hide? health]]
                       (or ally? hide? (> distance char-selection-distance-threshold) (zero? health))))
                   (sort-by second)
                   ffirst)]
    (do
      (pc/set-selected-char-color :enemy)
      (st/set-selected-player id npc?))
    (st/cancel-selected-player)))

(defn- select-closest-enemy [e]
  (when (and (st/alive?) (= "KeyZ" (j/get-in e [:event :code])))
    (select-closest-enemy* false)))

(defn- select-closest-enemy-npc [e]
  (when (and (st/alive?) (= "KeyX" (j/get-in e [:event :code])))
    (select-closest-enemy* true)))

(defn- look-at-&-run-towards-selected-player [e]
  (when (and (st/alive?) (= "KeyR" (j/get-in e [:event :code])))
    (when-let [selected-player (if (j/get player :selected-enemy-npc?)
                                 (some-> (st/get-selected-player-id) (st/get-npc-entity))
                                 (some-> (st/get-selected-player-id) (st/get-other-player-entity)))]
      (st/process-running)
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
                      (when (st/chat-closed?)
                        (process-esc e)
                        (when (and (not (j/get-in e [:event :metaKey]))
                                   (not (j/get-in e [:event :ctrlKey]))
                                   (not (j/get-in e [:event :altKey])))
                          (process-skills e))
                        (select-closest-enemy e)
                        (select-closest-enemy-npc e)
                        (look-at-&-run-towards-selected-player e))))
    (pc/on-keyboard :EVENT_KEYUP
                    (fn [e]
                      (st/process-running)))
    (skills/register-skill-events events)
    (on :update-skills-order skills/register-key->skills)
    (on :process-skills-from-skill-bar-clicks process-skills)))

(let [last-selected #js {:time (js/Date.now)}
      key-r-e (clj->js {:event {:code "KeyR"}})]
  (defn- get-selected-enemy-id [e]
    (let [result (pc/raycast-rigid-body e entity.camera/entity)
          hit-entity-name (j/get-in result [:entity :name])]
      (when (= "enemy_player" hit-entity-name)
        (let [enemy-id (j/get-in result [:entity :id])
              enemy (st/get-other-player enemy-id)
              enemy-hidden? (j/get enemy :hide?)]
          (when (or (not enemy-hidden?)
                    (and enemy-hidden? (has-phantom-vision?)))
            (when (< (- (js/Date.now) (j/get last-selected :time)) 250)
              (look-at-&-run-towards-selected-player key-r-e)
              (st/process-running))
            (j/assoc! last-selected :time (js/Date.now))
            (str enemy-id)))))))

(defn- set-target-position [e]
  (when (st/alive?)
    (let [result (st/get-closest-terrain-hit e)
          hit-entity-name (j/get-in result [:entity :name])]
      (when (= "terrain" hit-entity-name)
        (let [x (j/get-in result [:point :x])
              y (j/get-in result [:point :y])
              z (j/get-in result [:point :z])
              model-entity (st/get-model-entity)
              char-pos (pc/get-pos model-entity)]
          (when (not (skills/char-cant-run?))
            (pc/look-at model-entity x (j/get char-pos :y) z true))
          (j/assoc! player :target-pos (pc/setv (j/get player :target-pos) x y z)
                    :target-pos-available? true)
          (pc/set-locater-target x z)
          (st/process-running))))))

(defn- select-player-or-set-target [e]
  (if-let [ally-id (get-selected-ally-id e)]
    (do
      (pc/set-selected-char-color :ally)
      (st/set-selected-player ally-id))
    (if-let [enemy-id (get-selected-enemy-id e)]
      (do
        (pc/set-selected-char-color :enemy)
        (st/set-selected-player enemy-id))
      (if-let [enemy-npc-id (get-selected-enemy-npc-id e)]
        (do
          (pc/set-selected-char-color :enemy)
          (st/set-selected-player enemy-npc-id true))
        (set-target-position e)))))

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
                   (when (and (st/mage?) (pc/button? e :MOUSEBUTTON_LEFT))
                     (skills.mage/throw-nova e))))
    (pc/on-mouse :EVENT_MOUSEMOVE
                 (fn [e]
                   (when (and (st/mage?) (not (j/get player :mouse-left-locked?)))
                     (st/show-nova-circle e)))))

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
  (j/call-in entity [:collision :on] "collisionstart" collision-start))

(defonce template-entity-map
  (delay
    {"orc_warrior" (pc/find-by-name "orc_warrior")
     "orc_mage" (pc/find-by-name "orc_mage")
     "orc_priest" (pc/find-by-name "orc_priest")
     "orc_asas" (pc/find-by-name "orc_asas")
     "human_warrior" (pc/find-by-name "human_warrior")
     "human_mage" (pc/find-by-name "human_mage")
     "human_priest" (pc/find-by-name "human_priest")
     "human_asas" (pc/find-by-name "human_asas")}))

(defn- create-model-and-template-entity [{:keys [id entity race class other-player?]}]
  (let [template-entity-name (str race "_" class)
        model-entity-name (str race "_" class "_model")
        character-template-entity (try
                                    (pc/clone (get @template-entity-map template-entity-name))
                                    (catch js/Error _
                                      (throw (js/Error. (pr-str {:id id
                                                                 :template-entity-name template-entity-name
                                                                 :other-player? other-player?
                                                                 :race race
                                                                 :class class})))))
        _ (j/assoc! character-template-entity :name (str template-entity-name "_" id))
        character-model-entity (pc/find-by-name character-template-entity model-entity-name)]
    (j/assoc! character-template-entity :enabled true)
    (when other-player?
      (j/assoc! (pc/find-by-name character-model-entity (str race "_" class "_mesh")) :enabled false)
      (j/assoc! (pc/find-by-name character-model-entity (str race "_" class "_mesh_lod_0")) :enabled true))
    (pc/set-loc-pos character-template-entity 0 0 0)
    (pc/add-child entity character-template-entity)
    [character-template-entity character-model-entity]))

(defn- init-player [{:keys [id username class race mana health pos hp-potions mp-potions tutorials]} player-entity]
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
              :hp-potions hp-potions
              :mp-potions mp-potions
              :tutorials tutorials
              :heal-counter 0)
    (when pos
      (j/call-in player-entity [:rigidbody :teleport] x y z))
    (fire :ui-player-set-total-health-and-mana {:health health
                                                :mana mana})))

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
  (skills.effects/apply-effect-teleport player)
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

(defn- add-player-id-to-char-meshes [{:keys [id class race]} model-entity template-entity state]
  (let [lod-0 (pc/find-by-name model-entity (str race "_" class "_mesh_lod_0"))
        lod-1 (pc/find-by-name model-entity (str race "_" class "_mesh_lod_1"))
        lod-2 (pc/find-by-name model-entity (str race "_" class "_mesh_lod_2"))
        id (str id)]
    (j/assoc! model-entity :player_id id)
    (j/assoc! lod-0 :player_id id)
    (j/assoc! lod-1 :player_id id)
    (j/assoc! lod-2 :player_id id)
    (j/assoc! state
              :armature (pc/find-by-name model-entity "Armature")
              :char-name (pc/find-by-name template-entity "char_name")
              :lod-0 lod-0
              :lod-1 lod-1
              :lod-2 lod-2
              :anim-component (j/get model-entity :anim))))

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
          params {:id id
                  :entity entity
                  :username username
                  :class class
                  :race race
                  :enemy? enemy?
                  :other-player? true}
          _ (pc/add-child (pc/root) entity)
          [template-entity model-entity] (create-model-and-template-entity params)
          effects (utils/add-skill-effects template-entity)
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
      (utils/create-char-name-text (assoc params :template-entity template-entity))
      (create-skill-fns state true)
      (add-player-id-to-char-meshes params model-entity template-entity state)
      (if enemy?
        (j/call-in entity [:rigidbody :teleport] x y z)
        (pc/set-pos entity x y z))
      state)))

(defn- update-fleet-foot-cooldown-if-asas [class]
  (when (= "asas" class)
    (common/update-fleet-foot-cooldown-for-asas)))

(defn- set-default-camera-angle []
  (let [state entity.camera/state]
    (if (= "orc" (st/get-race))
      (do
        (pc/setv (j/get state :eulers) -10122.552 18017.66 0)
        (pc/setv (j/get state :target-angle) -19.82 138.27 0))
      (do
        (pc/setv (j/get state :eulers) -10285.025 16218.33 0)
        (pc/setv (j/get state :target-angle) -18.33 334.974 0)))))

(defn- init-fn [this player-data]
  (let [player-entity (j/get this :entity)
        _ (init-player player-data player-entity)
        opts {:id (:id player-data)
              :entity player-entity
              :username (j/get player :username)
              :race (j/get player :race)
              :class (j/get player :class)}
        [template-entity model-entity] (create-model-and-template-entity opts)]
    (utils/create-char-name-text (assoc opts :template-entity template-entity))
    (j/assoc! player :camera (pc/find-by-name "camera"))
    (j/assoc! player
              :this this
              :effects (utils/add-skill-effects template-entity)
              :template-entity template-entity
              :drop #js{:hp (pc/find-by-name "particle_potion_hp")
                        :mp (pc/find-by-name "particle_potion_mp")}
              :model-entity model-entity
              :entity player-entity)
    (create-skill-fns player)
    (register-keyboard-events)
    (register-mouse-events)
    (update-fleet-foot-cooldown-if-asas (j/get player :class))
    (fire :init-skills (keyword (j/get player :class)))
    (set-default-camera-angle)
    (on :create-players (fn [players]
                          (doseq [p players]
                            (st/add-player (create-player p)))))
    (on :cooldown-ready? (fn [{:keys [ready? skill]}]
                           (st/set-cooldown ready? skill)))
    (when-not dev?
      (j/assoc! (st/get-player-entity) :name (str (random-uuid))))
    (entity.base/unregister-base-trigger-events)
    (entity.base/register-base-trigger-events)))

(defn- play-running-sound [dt model-entity]
  (when (and (= "run" (pc/get-anim-state model-entity))
             (j/get player :on-ground?))
    (when (> (j/get player :sound-run-elapsed-time) (if (j/get player :fleet-foot?) 0.3 0.4))
      (if (j/get player :sound-run-1?)
        (do
          (st/play-sound "run_1")
          (j/assoc! player :sound-run-1? false))
        (do
          (st/play-sound "run_2")
          (j/assoc! player :sound-run-1? true)))
      (j/assoc! player :sound-run-elapsed-time 0))
    (j/update! player :sound-run-elapsed-time + dt)))

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
                dir (-> temp-dir (pc/sub pos) pc/normalize (pc/scale speed))
                target-distance-threshold (if (j/get player :selected-enemy-npc?) 0.5 0.2)]
            (if (>= (pc/distance target pos) target-distance-threshold)
              (pc/apply-force player-entity (j/get dir :x) 0 (j/get dir :z))
              (st/cancel-target-pos)))
          (when (st/chat-closed?)
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
            (when (st/pressing-wasd-or-has-target?)
              (pc/set-loc-euler model-entity 0 (j/get player :target-y) 0)
              (pc/set-anim-boolean model-entity "run" true))))
        (play-running-sound dt model-entity)))))

(on :chat-open? (fn [open?]
                  (j/assoc! player :chat-open? open?)))

(defn enable-effect [name]
  (j/assoc! (pc/find-by-name (st/get-player-entity) name) :enabled true))

(defn disable-effect [name]
  (j/assoc! (pc/find-by-name (st/get-player-entity) name) :enabled false))

(defn- update-fn [dt this]
  (process-movement dt this)
  (show-player-selection-circle))

(defn init [player-data]
  (pc/create-script :player
                    {:init (fnt
                             (set! player-script this)
                             (init-fn this player-data))
                     :update (fnt (update-fn dt this))
                     :post-init (fnt
                                  (register-collision-events (j/get this :entity))
                                  (fire :start-lod-manager))}))

(on :init
    (fn [player-data]
      (if player-script
        (do
          (entity.camera/register-camera-mouse-events)
          (init-fn player-script player-data))
        (init player-data))
      (fire :connect-to-world-state)))

(on :settings-updated
    (fn [settings]
      (doseq [[k v] settings]
        (case k
          :sound? (some-> (j/assoc-in! (st/get-player-entity) [:c :sound :volume] (if v 1 0)))
          :camera-rotation-speed (j/assoc! entity.camera/state :camera-rotation-speed v)
          :edge-scroll-speed (j/assoc! entity.camera/state :edge-scroll-speed v)
          :graphics-quality (j/assoc-in! pc/app [:graphicsDevice :maxPixelRatio] v)
          :fps? (if v
                  (j/call-in (pc/find-by-name "Root") [:c :script :fps :fps :show])
                  (j/call-in (pc/find-by-name "Root") [:c :script :fps :fps :hide]))
          :ping? (j/assoc! st/settings :ping? v)
          nil))))

(on :reset-tutorials
    (fn [tutorials]
      (j/assoc! st/player :tutorials tutorials)
      (utils/set-item "tutorials" (pr-str tutorials))
      (j/assoc! entity.camera/state :right-mouse-dragged? false)))
