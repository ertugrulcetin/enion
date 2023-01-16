(ns enion-cljs.scene.skills.mage
  (:require
    [applied-science.js-interop :as j]
    [enion-cljs.scene.keyboard :as k]
    [enion-cljs.scene.pc :as pc]
    [enion-cljs.scene.skills.core :as skills :refer [model-entity]])
  (:require-macros
    [enion-cljs.scene.macros :as m]))

;; TODO define combo rand ranges in a var
(def events
  (concat
    skills/common-states
    [{:anim-state "attackRange" :event "onAttackRangeEnd" :skill? true :end? true}
     {:anim-state "attackRange" :event "onAttackRangeCall" :call? true}
     {:anim-state "attackSingle" :event "onAttackSingleEnd" :skill? true :end? true}
     {:anim-state "attackSingle" :event "onAttackSingleCall" :call? true}
     {:anim-state "attackR" :event "onAttackREnd" :skill? true :end? true}
     {:anim-state "attackR" :event "onAttackRCall" :call? true}
     {:anim-state "teleport" :event "onTeleportCall" :call? true}
     {:anim-state "teleport" :event "onTeleportEnd" :skill? true :end? true}]))

(defn process-skills [e state]
  (when-not (-> e .-event .-repeat)
    (let [active-state (pc/get-anim-state model-entity)]
      (m/process-cancellable-skills ["attackRange" "attackSingle" "attackR" "teleport"] active-state state)
      (cond
        (and (= "idle" active-state) (k/pressing-wasd?))
        (pc/set-anim-boolean model-entity "run" true)

        (and (skills/idle-run-states active-state) (pc/key? e :KEY_SPACE) (j/get state :on-ground?))
        (pc/set-anim-boolean model-entity "jump" true)

        (and (skills/idle-run-states active-state) (skills/skill-pressed? e "attackRange"))
        (pc/set-anim-boolean model-entity "attackRange" true)

        (and (skills/idle-run-states active-state) (skills/skill-pressed? e "attackSingle"))
        (pc/set-anim-boolean model-entity "attackSingle" true)

        (and (skills/idle-run-states active-state) (skills/skill-pressed? e "teleport"))
        (pc/set-anim-boolean model-entity "teleport" true)

        (and (skills/idle-run-states active-state) (skills/skill-pressed? e "attackR"))
        (pc/set-anim-boolean model-entity "attackR" true)))))

;; TODO check temp-final-pos, when there are multiple novas, what to do? override may happen...
(defn create-throw-nova-fn [entity]
  (let [temp-first-pos #js {}
        temp-final-pos #js {}
        temp-final-scale #js {:x 5 :y 5 :z 5}
        opacity #js {:opacity 1}
        last-opacity #js {:opacity 0}]
    (fn [pos]
      (j/assoc! entity :enabled true)
      (j/assoc! temp-first-pos :x (j/get pos :x) :y (+ (j/get pos :y) 5) :z (j/get pos :z))
      (j/assoc! temp-final-pos :x (j/get pos :x) :y (j/get pos :y) :z (j/get pos :z))
      (let [tween-pos (-> (j/call entity :tween temp-first-pos)
                          (j/call :to temp-final-pos 0.5 js/pc.ExponentialIn))
            _ (j/call tween-pos :on "update"
                      (fn []
                        (pc/set-pos entity (j/get temp-first-pos :x) (j/get temp-first-pos :y) (j/get temp-first-pos :z))))
            _ (pc/set-loc-scale entity 0.2)
            first-scale (pc/get-loc-scale entity)
            tween-scale (-> (j/call entity :tween first-scale)
                            (j/call :to temp-final-scale 1 js/pc.Linear))
            _ (j/call-in entity [:render :meshInstances 0 :setParameter] "material_opacity" 1)
            _ (j/assoc! opacity :opacity 1)
            tween-opacity (-> (j/call entity :tween opacity)
                              (j/call :to last-opacity 1 js/pc.Linear)
                              (j/call :delay 0.5))
            _ (j/call tween-opacity :on "update"
                      (fn []
                        (j/call-in entity [:render :meshInstances 0 :setParameter] "material_opacity" (j/get opacity :opacity))))
            _ (j/call tween-opacity :on "complete"
                      (fn []
                        (println "complete!!")
                        (j/call-in entity [:children 0 :particlesystem :stop])
                        (j/call-in entity [:children 0 :particlesystem :reset])
                        (j/assoc! entity :enabled false)))]
        (j/call tween-pos :start)
        (j/call tween-scale :start)
        (j/call tween-opacity :start)
        (j/call-in entity [:children 0 :particlesystem :play])
        nil))))
