(ns enion-cljs.scene.skills.core
  (:require
    [applied-science.js-interop :as j]
    [enion-cljs.scene.keyboard :as k]
    [enion-cljs.scene.pc :as pc]))

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
   {:anim-state "run" :event "onRunStart"}
   {:anim-state "jump" :event "onJumpEnd" :end? true}
   {:anim-state "jump" :event "onJumpStart" :call? true :f (fn [player-entity]
                                                             (pc/apply-impulse player-entity 0 200 0))}])

(defn skill-cancelled? [anim-state active-state state]
  (and (= active-state anim-state)
       (not (j/get state :skill-locked?))
       (not (k/pressing-attacks?))))

(defn cancel-skill [anim-state]
  (pc/set-anim-boolean model-entity anim-state false)
  (pc/set-anim-boolean model-entity "run" true))

(defn skill-pressed? [e skill]
  (= (key->skill (.-key e)) skill))

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
                    (f player-entity))
                  (when end?
                    (pc/set-anim-boolean model-entity anim-state false)
                    (when (k/pressing-wasd?)
                      (pc/set-anim-boolean model-entity "run" true))
                    (when skill?
                      (j/assoc! state
                                :skill-locked? false
                                :can-r-attack-interrupt? false)))
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
