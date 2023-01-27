(ns enion-cljs.scene.skills.priest
  (:require
    [applied-science.js-interop :as j]
    [enion-cljs.scene.keyboard :as k]
    [enion-cljs.scene.pc :as pc]
    [enion-cljs.scene.skills.core :as skills]
    [enion-cljs.scene.skills.effects :as skills.effects]
    [enion-cljs.scene.states :refer [player get-model-entity]])
  (:require-macros
    [enion-cljs.scene.macros :as m]))

(def events
  (concat skills/common-states
          [{:anim-state "breakDefense" :event "onBreakDefenseEnd" :skill? true :end? true}
           {:anim-state "breakDefense" :event "onBreakDefenseCall" :call? true}
           {:anim-state "heal" :event "onHealEnd" :skill? true :end? true}
           {:anim-state "heal" :event "onHealCall" :call? true}
           {:anim-state "attackR" :event "onAttackREnd" :skill? true :end? true}
           {:anim-state "attackR" :event "onAttackRCall" :call? true}
           {:anim-state "cure" :event "onCureCall" :call? true}
           {:anim-state "cure" :event "onCureEnd" :skill? true :end? true}]))

(defn process-skills [e]
  (when-not (j/get-in e [:event :repeat])
    (let [model-entity (get-model-entity)
          active-state (pc/get-anim-state model-entity)]
      (m/process-cancellable-skills
        ["attackR" "breakDefense" "heal" "cure"]
        (j/get-in e [:event :code])
        active-state
        player)
      (cond
        (and (= "idle" active-state) (k/pressing-wasd?))
        (pc/set-anim-boolean model-entity "run" true)

        (and (skills/idle-run-states active-state) (pc/key? e :KEY_SPACE) (j/get player :on-ground?))
        (pc/set-anim-boolean model-entity "jump" true)

        (and (skills/idle-run-states active-state) (skills/skill-pressed? e "breakDefense"))
        (do
          (pc/set-anim-boolean model-entity "breakDefense" true)
          (skills.effects/apply-effect-defense-break-particles player))

        (and (skills/idle-run-states active-state) (skills/skill-pressed? e "heal"))
        (do
          (pc/set-anim-boolean model-entity "heal" true)
          (skills.effects/apply-effect-heal-particles player))

        (and (skills/idle-run-states active-state) (skills/skill-pressed? e "cure"))
        (do
          (pc/set-anim-boolean model-entity "cure" true)
          (skills.effects/apply-effect-cure-particles player))

        (and (skills/idle-run-states active-state) (skills/skill-pressed? e "attackR"))
        (pc/set-anim-boolean model-entity "attackR" true)))))
