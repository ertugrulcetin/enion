(ns enion-cljs.scene.entities.npc
  (:require
    [applied-science.js-interop :as j]
    [camel-snake-kebab.core :as csk]
    [enion-cljs.scene.pc :as pc]
    [enion-cljs.scene.states :as st]
    [enion-cljs.scene.utils :as utils]
    [enion-cljs.utils :as common.utils]))

(defonce npcs #js {})
(defonce npc (delay (pc/find-by-name "undead_warrior")))

(defn init-npcs []
  (doseq [i (range 10)
          :let [entity (pc/clone @npc)
                model (pc/find-by-name entity "undead_warrior_model")
                i (inc i)]]
    (utils/create-char-name-text {:template-entity entity
                                  :username "Skeleton Warrior"
                                  :enemy? true
                                  :npc? true})
    (pc/add-child (pc/root) entity)
    (j/assoc! npcs i (clj->js {:id i
                               :entity entity
                               :model model
                               :prev-state :idle
                               :initial-pos {}
                               :last-pos {}
                               :from (pc/vec3)
                               :to (pc/vec3)}))))

(comment
  (init-npcs)
  (remove-npcs)
  )

(defn remove-npcs []
  (doseq [id (js/Object.keys npcs)]
    (j/call-in npcs [id :entity :destroy])
    (js-delete npcs id)))

(defn- interpolate-npc [npc-id new-x new-y new-z]
  (let [npc-entity (j/get-in npcs [npc-id :entity])
        initial-pos (j/get-in npcs [npc-id :initial-pos])
        last-pos (j/get-in npcs [npc-id :last-pos])
        current-pos (pc/get-pos npc-entity)
        current-x (j/get current-pos :x)
        current-y (j/get current-pos :y)
        current-z (j/get current-pos :z)]
    #_(pc/set-pos npc-entity new-x new-y new-z)
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

;; Skeleton Warrior
;; Bone Fighter
;; Skull Knight
;; Undead Soldier
;; Skeletal Champion
(defn process-npcs [current-player-id npcs-world-state]
  (doseq [npc npcs-world-state
          :let [npc-id (:id npc)
                npc-entity (j/get-in npcs [npc-id :entity])]
          :when npc-entity]
    (let [model (j/get-in npcs [npc-id :model])
          state (:state npc)
          new-x (:px npc)
          new-z (:pz npc)
          from (j/get-in npcs [npc-id :from])
          to (j/get-in npcs [npc-id :to])
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
          prev-state (j/get-in npcs [npc-id :prev-state])]
      (update-npc-anim-state model state prev-state)
      (when-not (= state :idle)
        (if target-player-id
          (pc/look-at model (j/get target-player-pos :x) new-y (j/get target-player-pos :z) true)
          (pc/look-at model new-x new-y new-z true)))
      (j/assoc-in! npcs [npc-id :prev-state] state)
      (interpolate-npc npc-id new-x new-y new-z))))
