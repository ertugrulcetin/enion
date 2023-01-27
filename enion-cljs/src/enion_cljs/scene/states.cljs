(ns enion-cljs.scene.states
  (:require
    [applied-science.js-interop :as j]
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

(defn get-player-entity []
  (j/get player :entity))

(defn get-model-entity
  ([]
   (j/get player :model-entity))
  ([player-id]
   (j/get-in other-players [player-id :model-entity])))

(defn destroy-player [player-id]
  (j/call-in other-players [player-id :entity :destroy])
  (js-delete other-players player-id))

(defn destroy-players []
  (doseq [id (js/Object.keys other-players)]
    (destroy-player id)))

(defn get-other-player [player-id]
  (j/get other-players player-id))

(defn get-other-player-entity [id]
  (j/get-in other-players [id :entity]))

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
