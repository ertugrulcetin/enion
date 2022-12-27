(ns enion-cljs.scene.skills.priest
  (:require
    [applied-science.js-interop :as j]
    [enion-cljs.scene.keyboard :as k]
    [enion-cljs.scene.pc :as pc]
    [enion-cljs.scene.skills.core :as skills :refer [model-entity]])
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

(defn process-skills [e state]
  (when-not (j/get-in e [:event :repeat])
    (let [active-state (pc/get-anim-state model-entity)]
      (m/process-cancellable-skills ["attackR" "breakDefense" "heal" "cure"] active-state state)
      (cond
        (and (= "idle" active-state) (k/pressing-wasd?))
        (pc/set-anim-boolean model-entity "run" true)

        (and (skills/idle-run-states active-state) (pc/key? e :KEY_SPACE) (j/get state :on-ground?))
        (pc/set-anim-boolean model-entity "jump" true)

        (and (skills/idle-run-states active-state) (skills/skill-pressed? e "breakDefense"))
        (pc/set-anim-boolean model-entity "breakDefense" true)

        (and (skills/idle-run-states active-state) (skills/skill-pressed? e "heal"))
        (pc/set-anim-boolean model-entity "heal" true)

        (and (skills/idle-run-states active-state) (skills/skill-pressed? e "cure"))
        (pc/set-anim-boolean model-entity "cure" true)

        (and (skills/idle-run-states active-state) (skills/skill-pressed? e "attackR"))
        (pc/set-anim-boolean model-entity "attackR" true)))))
