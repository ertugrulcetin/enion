(ns enion-cljs.scene.skills.asas
  (:require
    [applied-science.js-interop :as j]
    [enion-cljs.scene.keyboard :as k]
    [enion-cljs.scene.pc :as pc]
    [enion-cljs.scene.skills.core :as skills :refer [model-entity state]]
    [enion-cljs.scene.utils :as utils])
  (:require-macros
    [enion-cljs.scene.macros :as m]))

;; TODO define combo rand ranges in a var
(def events
  (concat
    skills/common-states
    [{:anim-state "attackDagger" :event "onAttackDaggerEnd" :skill? true :end? true}
     {:anim-state "attackDagger" :event "onAttackDaggerCall" :call? true}
     {:anim-state "attackDagger" :event "onAttackDaggerLock" :r-lock? true}
     {:anim-state "attackDagger" :event "onAttackDaggerLockRelease" :r-release? true}
     {:anim-state "attackR" :event "onAttackREnd" :skill? true :end? true}
     {:anim-state "attackR" :event "onAttackRCall" :call? true}
     {:anim-state "attackR" :event "onAttackRLock" :r-lock? true}
     {:anim-state "attackR" :event "onAttackRLockRelease" :r-release? true}
     {:anim-state "hide" :event "onHideCall" :call? true}
     {:anim-state "hide" :event "onHideEnd" :skill? true :end? true}]))

(def last-one-hand-combo (atom (js/Date.now)))

(let [initial-opacity #js {:opacity 1}
      last-opacity #js {:opacity 0.3}
      entity (delay
               (let [race (j/get state :race)
                     class (j/get state :class)]
                 (pc/find-by-name model-entity (str race "_" class "_mesh"))))]
  (defn- hide []
    (let [_ (j/assoc! initial-opacity :opacity 1)
          entity @entity
          tween-opacity (-> (j/call entity :tween initial-opacity)
                            (j/call :to last-opacity 2 js/pc.Linear))
          _ (j/call tween-opacity :on "update"
                    (fn []
                      (j/call-in entity [:render :meshInstances 0 :setParameter] "material_opacity" (j/get initial-opacity :opacity))))]
      (j/call tween-opacity :start)
      nil)))

(defn process-skills [e state]
  (when-not (-> e .-event .-repeat)
    (let [active-state (pc/get-anim-state model-entity)]
      (m/process-cancellable-skills ["attackDagger" "attackR" "hide"] active-state state)
      (cond
        (and (= active-state "attackDagger")
             (skills/skill-pressed? e "attackR")
             (j/get state :can-r-attack-interrupt?))
        (do
          (println "R combo!")
          (pc/set-anim-boolean model-entity "attackDagger" false)
          (pc/set-anim-boolean model-entity "attackR" true))

        (and (= active-state "attackR")
             (skills/skill-pressed? e "attackDagger")
             (j/get state :can-r-attack-interrupt?)
             (> (- (js/Date.now) @last-one-hand-combo) (utils/rand-between 750 1200)))
        (do
          (println "dagger combo...!")
          (pc/set-anim-boolean model-entity "attackR" false)
          (pc/set-anim-boolean model-entity "attackDagger" true)
          (reset! last-one-hand-combo (js/Date.now)))

        (and (= "idle" active-state) (k/pressing-wasd?))
        (pc/set-anim-boolean model-entity "run" true)

        (and (skills/idle-run-states active-state) (pc/key? e :KEY_SPACE) (j/get state :on-ground?))
        (pc/set-anim-boolean model-entity "jump" true)

        (and (skills/idle-run-states active-state) (skills/skill-pressed? e "attackDagger"))
        (pc/set-anim-boolean model-entity "attackDagger" true)

        (and (skills/idle-run-states active-state) (skills/skill-pressed? e "hide"))
        (do
          (pc/set-anim-boolean model-entity "hide" true)
          (hide))

        (and (skills/idle-run-states active-state) (skills/skill-pressed? e "attackR"))
        (pc/set-anim-boolean model-entity "attackR" true)))))
