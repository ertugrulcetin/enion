(ns enion-cljs.scene.skills.core
  (:require
    [applied-science.js-interop :as j]
    [enion-cljs.common :as common :refer [fire]]
    [enion-cljs.scene.keyboard :as k]
    [enion-cljs.scene.network :as net :refer [dispatch-pro]]
    [enion-cljs.scene.pc :as pc]
    [enion-cljs.scene.states :refer [player get-model-entity get-player-entity]]))

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

;; TODO eger karakter stateti idle ve run degilse, ve belirlenen sureden fazla o statete kalmissa duzenleme yap
;; networkten gelen koddan dolayi sikinti olabilir
(def common-states
  [{:anim-state "idle" :event "onIdleStart"}
   {:anim-state "run"
    :event "onRunStart"
    :f (fn [_]
         (when (j/get player :fleet-foot?)
           (pc/update-anim-speed (get-model-entity) "run" 1.5)))}
   {:anim-state "jump" :event "onJumpEnd" :end? true}
   {:anim-state "jump" :event "onJumpStart" :call? true :f (fn [player-entity _]
                                                             (pc/apply-impulse player-entity 0 200 0))}])

(defmulti skill-response ffirst)

(defmethod net/dispatch-pro-response :skill [params]
  (js/console.log params))

(defn can-skill-be-cancelled? [anim-state active-state state]
  (and (= active-state anim-state)
       (not (j/get state :skill-locked?))))

(defn cancel-skill [anim-state]
  (let [model-entity (get-model-entity)]
    (pc/set-anim-boolean model-entity anim-state false)
    (pc/set-anim-boolean model-entity "run" true)))

(defn skill-pressed? [e skill]
  (= (key->skill (j/get e :key)) skill))

;; TODO remove this
(def latency 100)

(defn register-skill-events [events]
  (let [player-entity (get-player-entity)
        model-entity (get-model-entity)]
    (doseq [{:keys [anim-state
                    event
                    skill?
                    call?
                    end?
                    r-lock?
                    r-release?
                    f
                    call-name]} events]
      (pc/on-anim model-entity event
                  (fn []
                    (when f
                      (f player-entity))
                    (when end?
                      (pc/set-anim-boolean model-entity anim-state false)
                      (when (k/pressing-wasd?)
                        (pc/set-anim-boolean model-entity "run" true))
                      (when skill?
                        (j/assoc! player
                                  :skill-locked? false
                                  :can-r-attack-interrupt? false))
                      (when-let [target (and (skills-char-cant-run anim-state)
                                             (j/get player :target-pos-available?)
                                             (j/get player :target-pos))]
                        (pc/look-at model-entity (j/get target :x) (j/get (pc/get-pos model-entity) :y) (j/get target :z) true)))
                    (cond
                      call? (let [selected-player-id (j/get-in player [:skill->selected-player-id anim-state])]
                              (j/assoc! player :skill-locked? true)
                              (j/assoc-in! player [:skill->selected-player-id anim-state] nil)

                              (dispatch-pro :skill (cond-> {:skill anim-state}
                                                     selected-player-id (assoc :selected-player-id selected-player-id)))
                              #_(when call-name
                        (js/setTimeout
                          #(fire call-name selected-player-id)
                          latency)))
                      r-release? (j/assoc! player :can-r-attack-interrupt? true)
                      r-lock? (j/assoc! player :can-r-attack-interrupt? false)))))))

(defn register-key->skills [skill-mapping]
  (let [m (reduce-kv (fn [acc k v]
                       (assoc acc (pc/get-code (keyword (str "KEY_" k))) v))
                     {(pc/get-code :KEY_R) "attackR"}
                     skill-mapping)]
    (set! key->skill m)))

(defn char-cant-run? []
  (skills-char-cant-run (pc/get-anim-state (get-model-entity))))
