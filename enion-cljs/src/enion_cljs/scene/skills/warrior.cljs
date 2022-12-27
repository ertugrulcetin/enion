(ns enion-cljs.scene.skills.warrior
  (:require
    [applied-science.js-interop :as j]
    [enion-cljs.scene.keyboard :as k]
    [enion-cljs.scene.pc :as pc]
    [enion-cljs.scene.skills.core :as skills :refer [model-entity]]
    [enion-cljs.scene.utils :as utils])
  (:require-macros
    [enion-cljs.scene.macros :as m]))

;; TODO current state leftover in idle mode but was able to run at the same time
(def events
  (concat
    skills/common-states
    [{:anim-state "attackOneHand" :event "onAttackOneHandEnd" :skill? true :end? true}
     {:anim-state "attackOneHand" :event "onAttackOneHandCall" :call? true}
     {:anim-state "attackOneHand" :event "onAttackOneHandLockRelease" :r-release? true}
     {:anim-state "attackOneHand" :event "onAttackOneHandLock" :r-lock? true}
     {:anim-state "attackR" :event "onAttackREnd" :skill? true :end? true}
     {:anim-state "attackR" :event "onAttackRCall" :call? true}
     {:anim-state "attackR" :event "onAttackRLockRelease" :r-release? true}
     {:anim-state "attackR" :event "onAttackRLock" :r-lock? true}
     {:anim-state "attackSlowDown" :event "onAttackSlowDownCall" :call? true}
     {:anim-state "attackSlowDown" :event "onAttackSlowDownEnd" :skill? true :end? true}]))

(def last-one-hand-combo (atom (js/Date.now)))

;; TODO can't jump while attacking - collision wise
(defn process-skills [e state]
  (when-not (-> e .-event .-repeat)
    (let [active-state (pc/get-anim-state model-entity)]
      (m/process-cancellable-skills ["attackOneHand" "attackSlowDown" "attackR"] active-state state)
      (cond
        (and (= active-state "attackOneHand")
             (skills/skill-pressed? e "attackR")
             (j/get state :can-r-attack-interrupt?))
        (do
          (println "R combo!")
          (pc/set-anim-boolean model-entity "attackOneHand" false)
          (pc/set-anim-boolean model-entity "attackR" true))

        (and (= active-state "attackR")
             (skills/skill-pressed? e "attackOneHand")
             (j/get state :can-r-attack-interrupt?)
             (> (- (js/Date.now) @last-one-hand-combo) (utils/rand-between 750 1200)))
        (do
          (println "one hand combo...!")
          (pc/set-anim-boolean model-entity "attackR" false)
          (pc/set-anim-boolean model-entity "attackOneHand" true)
          (reset! last-one-hand-combo (js/Date.now)))

        (and (= "idle" active-state) (k/pressing-wasd?))
        (pc/set-anim-boolean model-entity "run" true)

        (and (skills/idle-run-states active-state) (pc/key? e :KEY_SPACE) (j/get state :on-ground?))
        (pc/set-anim-boolean model-entity "jump" true)

        (and (skills/idle-run-states active-state) (skills/skill-pressed? e "attackOneHand"))
        (pc/set-anim-boolean model-entity "attackOneHand" true)

        (and (skills/idle-run-states active-state) (skills/skill-pressed? e "attackSlowDown"))
        (pc/set-anim-boolean model-entity "attackSlowDown" true)

        (and (skills/idle-run-states active-state) (skills/skill-pressed? e "attackR"))
        (pc/set-anim-boolean model-entity "attackR" true)))))
