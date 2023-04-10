(ns enion-cljs.scene.entities.npc
  (:require
    [applied-science.js-interop :as j]
    [camel-snake-kebab.core :as csk]
    [common.enion.npc :as common.npc]
    [enion-cljs.common :refer [fire]]
    [enion-cljs.scene.pc :as pc]
    [enion-cljs.scene.skills.effects :as effects]
    [enion-cljs.scene.states :as st]
    [enion-cljs.scene.utils :as utils]
    [enion-cljs.utils :as common.utils]))

(defonce npc-template-entities (delay {:skeleton-warrior (pc/find-by-name "skeleton_warrior")}))

(defn init-npcs [npcs]
  (doseq [[npc-type ids] npcs
          id ids
          :let [entity (pc/clone (npc-type @npc-template-entities))
                npc-type-name (csk/->snake_case_string npc-type)
                model (pc/find-by-name entity (str npc-type-name "_model"))
                effects (utils/add-skill-effects model)
                effects-entity (pc/find-by-name entity "effects")
                lod-0 (pc/find-by-name model (str npc-type-name "_mesh_lod_0"))
                lod-1 (pc/find-by-name model (str npc-type-name "_mesh_lod_1"))
                lod-2 (pc/find-by-name model (str npc-type-name "_mesh_lod_2"))
                id-str (str id)
                npc-name (-> common.npc/npcs npc-type :name)]]
    (j/assoc! model :npc_id id-str)
    (j/assoc! lod-0 :npc_id id-str)
    (j/assoc! lod-1 :npc_id id-str)
    (j/assoc! lod-2 :npc_id id-str)
    (utils/create-char-name-text {:template-entity entity
                                  :username npc-name
                                  :enemy? true
                                  :npc? true})
    (pc/add-child (pc/root) entity)
    ;; due to 0.002 scale, we had to multiply by 500
    (pc/set-loc-scale effects-entity 500)
    (pc/set-loc-pos effects-entity 0 75 0)
    (j/assoc! st/npcs id (clj->js {:id id
                                   :username npc-name
                                   :entity entity
                                   :effects effects
                                   :enemy? true
                                   :health (-> common.npc/npcs npc-type :health)
                                   :total-health (-> common.npc/npcs npc-type :health)
                                   :model-entity model
                                   :prev-state :idle
                                   :initial-pos {}
                                   :last-pos {}
                                   :from (pc/vec3)
                                   :to (pc/vec3)
                                   :armature (pc/find-by-name model "Armature")
                                   :char-name (pc/find-by-name entity "char_name")
                                   :lod-0 lod-0
                                   :lod-1 lod-1
                                   :lod-2 lod-2
                                   :anim-component (j/get model :anim)}))))

(comment
  st/npcs
  (init-npcs)
  (effects/apply-effect-attack-slow-down (j/get st/npcs 1))
  (effects/apply-effect-attack-dagger (j/get st/npcs 1))
  (effects/apply-effect-attack-flame (j/get st/npcs 1))
  (effects/apply-effect-attack-r (j/get st/npcs 1))
  (effects/apply-effect-got-defense-break (j/get st/npcs 1))
  )

(defn- interpolate-npc [npc-id new-x new-y new-z]
  (let [npc-entity (j/get-in st/npcs [npc-id :entity])
        initial-pos (j/get-in st/npcs [npc-id :initial-pos])
        last-pos (j/get-in st/npcs [npc-id :last-pos])
        current-pos (pc/get-pos npc-entity)
        current-x (j/get current-pos :x)
        current-y (j/get current-pos :y)
        current-z (j/get current-pos :z)]
    (when (and current-x new-x current-z new-z
               (or (not= (common.utils/parse-float current-x 2)
                         (common.utils/parse-float new-x 2))
                   (not= (common.utils/parse-float current-z 2)
                         (common.utils/parse-float new-z 2))))
      (let [tween-interpolation (-> (j/call npc-entity :tween initial-pos)
                                    (j/call :to last-pos 0.05 pc/linear))]
        (j/assoc! initial-pos :x current-x :y current-y :z current-z)
        (j/assoc! last-pos :x new-x :y new-y :z new-z)
        (j/call tween-interpolation :on "update"
                (fn []
                  (let [x (j/get initial-pos :x)
                        y (j/get initial-pos :y)
                        z (j/get initial-pos :z)]
                    (pc/set-pos npc-entity x y z))))
        (j/call tween-interpolation :start)))))

(defn- filter-hit-by-terrain [result]
  (when (= "terrain" (j/get-in result [:entity :name]))
    result))

(defn- get-closest-hit-of-terrain [result]
  (j/get-in result [:point :y]))

(defn- update-npc-anim-state [model state prev-state]
  (when (not= state prev-state)
    (pc/set-anim-boolean model (csk/->camelCaseString prev-state) false)
    (pc/set-anim-boolean model (csk/->camelCaseString state) true)))

(defonce temp-selected-player #js {})

(defn set-health [id health]
  (j/assoc-in! st/npcs [id :health] health)
  (when-let [selected-npc-id (some-> (st/get-selected-player-id) (js/parseInt))]
    (when (= selected-npc-id id)
      (when-let [npc (st/get-npc selected-npc-id)]
        (let [username (j/get npc :username)
              health (j/get npc :health)
              total-health (j/get npc :total-health)
              enemy? (j/get npc :enemy?)]
          (j/assoc! temp-selected-player
                    :username username
                    :health health
                    :total-health total-health
                    :enemy? enemy?)
          (fire :ui-selected-player temp-selected-player))))))

(defn get-npc-name [id]
  (j/get-in st/npcs [id :username]))

;; Skeleton Warrior
;; Bone Fighter
;; Skull Knight
;; Undead Soldier
;; Skeletal Champion
(defonce npc-id->last-time-died #js {})

(defn process-npcs [current-player-id npcs-world-state]
  (doseq [npc npcs-world-state
          :let [npc-id (:id npc)
                npc-entity (j/get-in st/npcs [npc-id :entity])]
          :when npc-entity]
    (let [model (j/get-in st/npcs [npc-id :model-entity])
          state (:state npc)
          new-x (:px npc)
          new-z (:pz npc)
          from (j/get-in st/npcs [npc-id :from])
          to (j/get-in st/npcs [npc-id :to])
          _ (pc/setv from new-x 10 new-z)
          _ (pc/setv to new-x -1 new-z)
          hit (->> (pc/raycast-all from to)
                   (filter filter-hit-by-terrain)
                   (sort-by get-closest-hit-of-terrain)
                   first)
          new-y (or (some-> hit (j/get-in [:point :y])) 0.55)
          target-player-id (:target-player-id npc)
          target-player-pos (when target-player-id
                              (if (= current-player-id target-player-id)
                                (st/get-pos)
                                (st/get-pos target-player-id)))
          prev-state (j/get-in st/npcs [npc-id :prev-state])
          re-spawned? (and (not= :die state)
                           (= :die prev-state)
                           (pc/disabled? npc-entity))]
      (if re-spawned?
        (pc/set-pos npc-entity new-x new-y new-z)
        (interpolate-npc npc-id new-x new-y new-z))
      (set-health npc-id (:health npc))
      (when (and (= :die state) (not= state prev-state))
        (j/assoc! npc-id->last-time-died npc-id (js/Date.now)))
      (when (and (= :die state)
                 (= state prev-state)
                 (>= (- (js/Date.now) (j/get npc-id->last-time-died npc-id)) 3000)
                 (pc/enabled? npc-entity))
        (pc/disable npc-entity)
        (when (some-> (st/get-selected-player-id) (js/parseInt) (= npc-id))
          (st/cancel-selected-player)))
      (when re-spawned?
        (pc/enable npc-entity))
      (update-npc-anim-state model state prev-state)
      (when-not (= state :idle)
        (if target-player-id
          (pc/look-at model (j/get target-player-pos :x) new-y (j/get target-player-pos :z) true)
          (pc/look-at model new-x new-y new-z true)))
      (j/assoc-in! st/npcs [npc-id :prev-state] state))))
