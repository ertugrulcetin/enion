(ns enion-cljs.scene.states
  (:require
    [applied-science.js-interop :as j]
    [clojure.data :as data]
    [enion-cljs.common :refer [fire]]
    [enion-cljs.scene.pc :as pc]))

(defonce player (clj->js {:speed 550
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
                          :sound-run-elapsed-time 0}))

(defonce other-players #js {})
(defonce settings #js {})

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

(defn destroy-player [player-id]
  (when-let [entity (get-other-player-entity player-id)]
    (j/call entity :destroy)
    (js-delete other-players player-id)))

(defn destroy-players []
  (doseq [id (js/Object.keys other-players)]
    (destroy-player id)))

;; TODO when other player's health changes (network tick), in there we will trigger :ui-selected-player
(def temp-selected-player #js {})

(defn set-selected-player [player-id]
  (j/assoc! player :selected-player-id player-id)
  (if-let [selected-player (get-other-player player-id)]
    (let [username (j/get selected-player :username)
          health (j/get selected-player :health)
          enemy? (j/get selected-player :enemy?)]
      (j/assoc! temp-selected-player
                :username username
                :health health
                :enemy? enemy?)
      (fire :ui-selected-player temp-selected-player))
    (fire :ui-selected-player nil)))

(defn cancel-selected-player []
  (pc/set-selected-player-position)
  (set-selected-player nil))

(defn enemy? [id]
  (j/get (get-other-player id) :enemy?))

(defn get-selected-player-id []
  (j/get player :selected-player-id))

(defn enemy-selected? [player-id]
  (j/get-in other-players [player-id :enemy?]))

(defn ally-selected? [player-id]
  (boolean
    (when player-id
      (not (j/get-in other-players [player-id :enemy?])))))

(defn alive?
  ([]
   (> (j/get player :health) 0))
  ([player-id]
   (> (j/get-in other-players [player-id :health]) 0)))

(defn distance-to [player-id]
  (pc/distance (pc/get-pos (get-player-entity)) (pc/get-pos (get-other-player-entity player-id))))

(defn add-player [player]
  (j/assoc! other-players (j/get player :id) player))

(defn disable-player-collision [player-id]
  (j/assoc-in! (get-other-player-entity player-id) [:collision :enabled] false))

(defn enable-player-collision [player-id]
  (j/assoc-in! (get-other-player-entity player-id) [:collision :enabled] true))

;; TODO use kezban lib for nested when-lets
(defn set-health
  ([health]
   (j/assoc! player :health health)
   (fire :ui-player-health health))
  ([player-id health]
   (j/assoc-in! other-players [player-id :health] health)
   (when-let [id (get-selected-player-id)]
     (when (= id player-id)
       (when-let [player (get-other-player id)]
         (let [username (j/get player :username)
               health (j/get player :health)
               enemy? (j/get player :enemy?)]
           (j/assoc! temp-selected-player
                     :username username
                     :health health
                     :enemy? enemy?)
           (fire :ui-selected-player temp-selected-player)))))))

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
    (let [model-entity (get-model-entity)
          pos (pc/get-pos model-entity)
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
      result)))

(defn move-player [player entity x y z]
  (if (j/get player :enemy?)
    (j/call-in entity [:rigidbody :teleport] x y z)
    (pc/set-pos entity x y z)))

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
        (fire :ui-send-msg not-enough-mana-msg))
      result)))
