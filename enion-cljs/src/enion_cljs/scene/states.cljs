(ns enion-cljs.scene.states
  (:require
    [applied-science.js-interop :as j]
    [clojure.data :as data]
    [enion-cljs.common :refer [fire on dev?]]
    [enion-cljs.scene.keyboard :as k]
    [enion-cljs.scene.pc :as pc]))

(def speed 550)
(def speed-fleet-foot 750)
(def speed-fleet-foot-asas 900)

(def default-player-state
  {:speed speed
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
   :hit-position (pc/vec3)
   :fleet-foot? false
   :sound-run-elapsed-time 0})

(defonce player (clj->js default-player-state))

(comment

  (j/assoc! player :speed 2550))

(defonce other-players #js {})

(defonce npcs #js {})

(defonce settings #js {})
(defonce camera-entity nil)
(defonce camera-state nil)

(defonce party-member-ids (volatile! []))

(def username-color (pc/color 2 2 2))
(def username-party-color (pc/color 2 2 0))
(def username-enemy-color (pc/color 2 0 0))

(on :on-ui-element?
    (fn [on-ui-element?]
      (j/assoc! settings :on-ui-element? on-ui-element?)))

(defn get-player-id []
  (j/get player :id))

(defn get-player-entity []
  (j/get player :entity))

(defn get-model-entity
  ([]
   (j/get player :model-entity))
  ([player-id]
   (j/get-in other-players [player-id :model-entity])))

(defn get-other-player [player-id]
  (j/get other-players player-id))

(defn get-other-player-entity [id]
  (j/get-in other-players [id :entity]))

(defn get-npc [id]
  (j/get npcs id))

(defn get-npc-entity [id]
  (j/get-in npcs [id :entity]))

(defn get-pos
  ([]
   (pc/get-pos (get-player-entity)))
  ([player-id]
   (pc/get-pos (get-other-player-entity player-id))))

(defn get-username
  ([]
   (j/get player :username))
  ([player-id]
   (-> player-id get-other-player (j/get :username))))

(defn destroy-player [player-id]
  (when-let [entity (get-other-player-entity player-id)]
    (j/call entity :destroy)
    (js-delete other-players player-id)
    (js-delete other-players js/undefined)))

(defn destroy-players []
  (doseq [id (js/Object.keys other-players)]
    (destroy-player id)))

(defn remove-npcs []
  (doseq [id (js/Object.keys npcs)]
    (j/call-in npcs [id :entity :destroy])
    (js-delete npcs id)))

(defonce temp-selected-player #js {})
(defonce prev-selected-player #js {})

;; TODO when other player's health changes (network tick), in there we will trigger :ui-selected-player
(let [selected-player-cancelled? (volatile! nil)]
  (defn set-selected-player
    ([id]
     (set-selected-player id false))
    ([id npc?]
     (j/assoc! player
               :selected-player-id id
               :selected-enemy-npc? npc?)
     (if-let [selected-player (if npc? (get-npc id) (get-other-player id))]
       (let [username (j/get selected-player :username)
             health (j/get selected-player :health)
             total-health (j/get selected-player :total-health)
             enemy? (or npc? (j/get selected-player :enemy?))
             level (j/get selected-player :level)]
         (j/assoc! temp-selected-player
                   :id id
                   :username username
                   :health health
                   :total-health total-health
                   :enemy? enemy?
                   :level level
                   :npc? npc?)
         (when (or (not= (j/get prev-selected-player :id) id)
                   (not= (j/get prev-selected-player :health) health))
           (fire :ui-selected-player temp-selected-player))
         (vreset! selected-player-cancelled? false)
         (j/assoc! prev-selected-player
                   :id id
                   :health health))
       (do
         (when-not @selected-player-cancelled?
           (fire :ui-selected-player nil))
         (vreset! selected-player-cancelled? true))))))

(on :select-party-member (fn [id]
                           (set-selected-player id)))

(defn cancel-selected-player []
  (pc/set-selected-player-position)
  (set-selected-player nil))

(defn enemy? [id]
  (j/get (get-other-player id) :enemy?))

(defn get-selected-player-id []
  (j/get player :selected-player-id))

(defn npc-selected? []
  (j/get player :selected-enemy-npc?))

(defn enemy-selected?
  ([player-id]
   (enemy-selected? player-id false))
  ([player-id npc?]
   (boolean
     (when player-id
       (j/get-in (if npc? npcs other-players) [player-id :enemy?])))))

(defn ally-selected? [player-id]
  (boolean
    (when player-id
      (not (j/get-in other-players [player-id :enemy?])))))

(defn alive?
  ([]
   (> (j/get player :health) 0))
  ([player-id]
   (alive? player-id false))
  ([player-id npc?]
   (> (j/get-in (if npc? npcs other-players) [player-id :health]) 0)))

(defn distance-to [player-id]
  (let [npc-or-player-entity (if (npc-selected?)
                               (get-npc-entity player-id)
                               (get-other-player-entity player-id))]
    (pc/distance (pc/get-pos (get-player-entity))
                 (pc/get-pos npc-or-player-entity))))

(defn distance-to-player
  ([player-id]
   (distance-to-player player-id false))
  ([player-id npc?]
   (pc/distance (pc/get-pos (get-player-entity))
                (pc/get-pos (if npc?
                              (get-npc-entity player-id)
                              (get-other-player-entity player-id))))))

(defn add-player [player]
  (if player
    (j/assoc! other-players (j/get player :id) player)
    (js/console.error "Player could not get created!")))

(defn disable-player-collision [player-id]
  (let [player (get-other-player player-id)
        player-entity (j/get player :entity)
        enemy? (j/get player :enemy?)]
    (when enemy?
      (j/assoc-in! player-entity [:collision :enabled] false))))

(defn enable-player-collision [player-id]
  (let [player (get-other-player player-id)
        player-entity (j/get player :entity)
        enemy? (j/get player :enemy?)]
    (when enemy?
      (j/assoc-in! player-entity [:collision :enabled] true))))

(def showed-low-health? (volatile! false))
(def low-health {:low-health true})

;; TODO use kezban lib for nested when-lets
(defn set-health
  ([health]
   (j/assoc! player :health health)
   (fire :ui-player-health health)
   (cond
     (and (not @showed-low-health?)
          (< health (* 0.3 (j/get player :total-health))))
     (do
       (vreset! showed-low-health? true)
       (fire :show-text low-health))

     (> health (* 0.3 (j/get player :total-health)))
     (vreset! showed-low-health? false)))
  ([player-id health]
   (j/assoc-in! other-players [player-id :health] health)
   (when-let [id (get-selected-player-id)]
     (when (= id player-id)
       (when-let [player (get-other-player id)]
         (let [username (j/get player :username)
               health (j/get player :health)
               total-health (j/get player :total-health)
               enemy? (j/get player :enemy?)]
           (j/assoc! temp-selected-player
                     :username username
                     :health health
                     :total-health total-health
                     :enemy? enemy?)
           (fire :ui-selected-player temp-selected-player)))))))

(defn get-health []
  (j/get player :health))

(defn set-mana [mana]
  (j/assoc! player :mana mana)
  (fire :ui-player-mana mana))

(defn set-cooldown [ready? skill]
  (j/assoc-in! player [:cooldown skill] ready?))

(defn cooldown-ready? [skill]
  (not (false? (j/get-in player [:cooldown skill]))))

(defn asas? []
  (= "asas" (j/get player :class)))

(defn mage? []
  (= "mage" (j/get player :class)))

(let [prev-state (volatile! {})]
  (defn get-state []
    (when (alive?)
      (let [entity (get-player-entity)
            pos (pc/get-pos entity)
            model-entity (get-model-entity)
            eul (pc/get-loc-euler model-entity)
            state {:px (j/get pos :x)
                   :py (j/get pos :y)
                   :pz (j/get pos :z)
                   :ex (j/get eul :x)
                   :ey (j/get eul :y)
                   :ez (j/get eul :z)
                   :st (pc/get-anim-state model-entity)}
            result (second (data/diff @prev-state state))]
        (vreset! prev-state state)
        result))))

(defn get-race []
  (j/get player :race))

(defn get-class []
  (j/get player :class))

(defn move-player
  ([[x y z]]
   (j/call-in (get-player-entity) [:rigidbody :teleport] x y z))
  ([player entity x y z]
   (if (j/get player :enemy?)
     (j/call-in entity [:rigidbody :teleport] x y z)
     (pc/set-pos entity x y z))))

(comment
  (move-player [34.79 1.5 -14.32]))

(defn rotate-player [player-id x y z]
  (when-let [entity (get-model-entity player-id)]
    (let [eul (pc/get-loc-euler entity)
          x1 (j/get eul :x)
          y1 (j/get eul :y)
          z1 (j/get eul :z)]
      (when (not (and (= x x1) (= y y1) (= z z1)))
        (pc/set-loc-euler entity x y z)))))

(defn set-anim-state [player-id state]
  (when-let [entity (get-model-entity player-id)]
    (let [current-anim-state (pc/get-anim-state entity)]
      (when-not (= state current-anim-state)
        (pc/set-anim-boolean entity current-anim-state false)
        (pc/set-anim-boolean entity state true)))))

(let [not-enough-mana-msg {:not-enough-mana true}]
  (defn enough-mana? [required-mana]
    (let [result (>= (j/get player :mana) required-mana)]
      (when-not result
        (fire :show-text not-enough-mana-msg))
      result)))

(let [temp (pc/vec3)]
  (defn get-closest-terrain-hit [e]
    (let [results (filter
                    (fn [result]
                      (when (= "terrain" (j/get-in result [:entity :name]))
                        result))
                    (pc/raycast-all-rigid-body e camera-entity))
          player-pos (pc/get-pos (get-player-entity))
          total-results (count results)]
      (cond
        (= 1 total-results) (first results)
        (> total-results 1) (first (sort-by
                                     (fn [result]
                                       (let [x (j/get-in result [:point :x])
                                             y (j/get-in result [:point :y])
                                             z (j/get-in result [:point :z])]
                                         (pc/setv temp x y z)
                                         (pc/distance player-pos temp)))
                                     results))
        :else nil))))

(defn show-nova-circle [e]
  (when (j/get player :positioning-nova?)
    (let [result (get-closest-terrain-hit e)]
      (when result
        (let [x (j/get-in result [:point :x])
              y (j/get-in result [:point :y])
              z (j/get-in result [:point :z])]
          (pc/set-nova-circle-pos player x y z))))))

(defn pressing-wasd-or-has-target? []
  (or (k/pressing-wasd?) (j/get player :target-pos-available?)))

(defn chat-open? []
  (j/get player :chat-open?))

(defn chat-closed? []
  (not (j/get player :chat-open?)))

(defn process-running []
  (when-let [model-entity (get-model-entity)]
    (if (or (and (chat-closed?) (pressing-wasd-or-has-target?))
            (and (chat-open?)
                 (j/get player :target-pos-available?)
                 (pressing-wasd-or-has-target?)))
      (pc/set-anim-boolean model-entity "run" true)
      (pc/set-anim-boolean model-entity "run" false))))

(defn cancel-target-pos []
  (j/assoc! player :target-pos-available? false)
  (pc/set-locater-target)
  (process-running))

(defn look-at-selected-player []
  (when-let [selected-player-id (get-selected-player-id)]
    (let [model-entity (get-model-entity)
          char-pos (pc/get-pos model-entity)
          selected-player (if (npc-selected?)
                            (get-npc-entity selected-player-id)
                            (get-other-player-entity selected-player-id))
          selected-player-pos (pc/get-pos selected-player)
          x (j/get selected-player-pos :x)
          z (j/get selected-player-pos :z)]
      (pc/look-at model-entity x (j/get char-pos :y) z true))))

(defn play-sound [track]
  (some-> (get-player-entity) (j/call-in [:c :sound :slots track :play])))

(on :play-sound play-sound)

(defn stop-sound [track]
  (some-> (get-player-entity) (j/call-in [:c :sound :slots track :stop])))

(defn tab-visible? []
  (let [tab-hidden? (j/get settings :tab-hidden)]
    (or (nil? tab-hidden?) (not tab-hidden?))))

(defn- disable-enable-npcs [hidden?]
  (if hidden?
    (doseq [id (js/Object.keys npcs)]
      (j/assoc-in! npcs [id :entity :enabled] (not hidden?)))
    (js/setTimeout
      (fn []
        (doseq [id (js/Object.keys npcs)]
          (j/assoc-in! npcs [id :entity :enabled] (not hidden?))))
      1500)))

(defn party-member? [player-id]
  (some #(= % (js/parseInt player-id)) @party-member-ids))

(defn level-up [{:keys [exp level health mana]}]
  (set-health health)
  (set-mana mana)
  (j/assoc! player
            :exp exp
            :level level
            :total-health health
            :total-mana mana))

(defn get-level []
  (j/get player :level))

(defn finished-quests? []
  (= #{:squid :ghoul :demon} (j/get player :quests)))

(on :tab-hidden
    (fn [hidden?]
      (process-running)
      (disable-enable-npcs hidden?)
      (j/assoc! settings :tab-hidden hidden?)))

(on :re-init
    (fn []
      (fire :close-socket-for-re-init)))

(on :socket-closed-for-re-init
    (fn []
      (destroy-players)
      (remove-npcs)
      (j/call (j/get player :template-entity) :destroy)
      (set! other-players #js {})
      (set! settings #js {})
      (set! player (clj->js default-player-state))
      (pc/off-all-keyboard-events)
      (pc/off-all-mouse-events)))

(comment
  camera-entity
  (js/console.log (get-player-entity))
  (pc/get-pos camera-entity)
  (move-player [24.849584579467773 3.627142071723938 -27.63292])
  (js/console.log (pc/get-pos (get-player-entity)))

  (do
    (pc/set-pos (get-player-entity) 0 0 0)
    (js/console.log (pc/get-pos (get-player-entity))))

  ; var pos = child.getPosition();
  ;    var rot = child.getRotation();
  ;
  ;    var scale = child.getLocalScale();
  ;
  ;    child.reparent(parent);
  ;
  ;    child.setPosition(pos);
  ;    child.setRotation(rot);
  ;    child.setLocalScale( new pc.Vec3(scale.x*scaleOffset, scale.y*scaleOffset, scale.z*scaleOffset) );

  (let [hand (pc/find-by-name (pc/find-by-name "player") "hand_r")
        weapon (pc/find-by-name "priest_mace")
        _ (pc/enable weapon)
        pos (j/call weapon :getPosition)
        rot (j/call weapon :getRotation)
        scale (j/call weapon :getLocalScale)
        _ (j/call weapon :reparent hand)]
    (j/call weapon :setPosition pos)
    (j/call weapon :setRotation rot)
    rot
    )

  )
