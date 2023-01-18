(ns enion-cljs.scene.skills.core
  (:require
    [applied-science.js-interop :as j]
    [enion-cljs.scene.keyboard :as k]
    [enion-cljs.scene.pc :as pc]))

(defonce state nil)
(defonce model-entity nil)

(def key->skill)

(def idle-run-states #{"idle" "run"})

(def skills-char-cant-run
  #{"hide"
    "attackRange"
    "attackSingle"
    "teleport"
    "breakDefense"
    "heal"
    "cure"})

(def common-states
  [{:anim-state "idle" :event "onIdleStart"}
   {:anim-state "run"
    :event "onRunStart"
    :f (fn [_ state]
         (when (j/get state :runs-fast?)
           (pc/update-anim-speed model-entity "run" 1.5)))}
   {:anim-state "jump" :event "onJumpEnd" :end? true}
   {:anim-state "jump" :event "onJumpStart" :call? true :f (fn [player-entity _]
                                                             (pc/apply-impulse player-entity 0 200 0))}])

(defn can-skill-be-cancelled? [anim-state active-state state]
  (and (= active-state anim-state)
       (not (j/get state :skill-locked?))))

(defn cancel-skill [anim-state]
  (pc/set-anim-boolean model-entity anim-state false)
  (pc/set-anim-boolean model-entity "run" true))

(defn skill-pressed? [e skill]
  (= (key->skill (j/get e :key)) skill))

(defn register-skill-events [state events player-entity]
  (doseq [{:keys [anim-state
                  event
                  skill?
                  call?
                  end?
                  r-lock?
                  r-release?
                  f]} events]
    (pc/on-anim model-entity event
                (fn []
                  (when f
                    (f player-entity state))
                  (when end?
                    (pc/set-anim-boolean model-entity anim-state false)
                    (when (k/pressing-wasd?)
                      (pc/set-anim-boolean model-entity "run" true))
                    (when skill?
                      (j/assoc! state
                                :skill-locked? false
                                :can-r-attack-interrupt? false))
                    (when-let [target (and (skills-char-cant-run anim-state)
                                           (j/get state :target-pos-available?)
                                           (j/get state :target-pos))]
                      (pc/look-at model-entity (j/get target :x) (j/get (pc/get-pos model-entity) :y) (j/get target :z) true)))
                  (cond
                    call? (j/assoc! state :skill-locked? true)
                    r-release? (j/assoc! state :can-r-attack-interrupt? true)
                    r-lock? (j/assoc! state :can-r-attack-interrupt? false))))))

(defn register-key->skills [skill-mapping]
  (let [m (reduce-kv (fn [acc k v]
                       (assoc acc (pc/get-code (keyword (str "KEY_" k))) v))
                     {(pc/get-code :KEY_R) "attackR"}
                     skill-mapping)]
    (set! key->skill m)))

(defn char-cant-run? []
  (skills-char-cant-run (pc/get-anim-state model-entity)))
