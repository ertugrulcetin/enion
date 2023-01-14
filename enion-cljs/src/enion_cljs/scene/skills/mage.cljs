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
(let [temp-final-pos (js-obj)
      temp-final-scale #js {:x 5 :y 5 :z 5}
      opacity #js {:opacity 1}
      last-opacity #js {:opacity 0}]
  (defn throw-nova [entity pos]
    (j/assoc! entity :enabled true)
    (j/assoc! temp-final-pos :x (j/get pos :x) :y (j/get pos :y) :z (j/get pos :z))
    (let [_ (pc/set-loc-pos entity (j/get pos :x) (+ (j/get pos :y) 5) (j/get pos :z))
          first-pos (pc/get-loc-pos entity)
          tween-loc (-> (j/call entity :tween first-pos)
                        (j/call :to temp-final-pos 0.5 js/pc.Linear))
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
                      (j/call-in entity [:children 0 :particlesystem :stop])
                      (j/call-in entity [:children 0 :particlesystem :reset])
                      (j/assoc! entity :enabled false)))]
      (j/call tween-loc :start)
      (j/call tween-scale :start)
      (j/call tween-opacity :start)
      (j/call-in entity [:children 0 :particlesystem :play])
      nil)))

(defn effect-scale-down [e duration state-id]
  (let [new-state-id (swap! state-id inc)
        _ (j/assoc! e :enabled true)
        _ (pc/set-loc-scale e 0.3)
        temp-final-scale #js {:x 0 :y 0.3 :z 0.3}
        first-scale (pc/get-loc-scale e)
        tween-scale (-> (j/call e :tween first-scale)
                        (j/call :to temp-final-scale duration js/pc.Linear))
        _ (j/call tween-scale :on "complete"
                  (fn []
                    (when (= new-state-id @state-id)
                      (j/assoc! e :enabled false))))]
    (j/call tween-scale :start)
    nil))

(defn effect-opacity-fade-out [e duration state-id]
  (let [new-state-id (swap! state-id inc)
        _ (j/assoc! e :enabled true)
        opacity #js {:opacity 1}
        last-opacity #js {:opacity 0}
        tween-opacity (-> (j/call e :tween opacity)
                          (j/call :to last-opacity duration js/pc.Linear))
        _ (j/call tween-opacity :on "update"
                  (fn []
                    (j/assoc-in! e [:sprite :opacity] (j/get opacity :opacity))))
        _ (j/call tween-opacity :on "complete"
                  (fn []
                    (when (= new-state-id @state-id)
                      (j/assoc! e :enabled false))))]
    (j/call tween-opacity :start)
    nil))

(def attack-dagger-one-hand
  (let [prev-timeout-id (atom nil)]
    (fn [e duration]
      (when-let [id @prev-timeout-id]
        (js/clearTimeout id))
      (j/assoc! e :enabled true)
      (reset! prev-timeout-id (js/setTimeout #(do
                                                (reset! prev-timeout-id nil)
                                                (j/assoc! e :enabled false))
                                             (* duration 1000))))))

(comment
  (effect-scale-down (pc/find-by-name (pc/find-by-name "player") "attack_slow_down") 0.2 (atom 0))
  (effect-scale-down (pc/find-by-name (pc/find-by-name "player") "portal") 2)

  (effect-opacity-fade-out (pc/find-by-name (pc/find-by-name "player") "attack_r") 0.2)
  (effect-opacity-fade-out (pc/find-by-name (pc/find-by-name "player") "attack_flame") 1)
  (effect-opacity-fade-out (pc/find-by-name (pc/find-by-name "player") "attack_dagger") 1)
  (effect-opacity-fade-out (pc/find-by-name (pc/find-by-name "player") "attack_one_hand") 1.5)

  (let [e (pc/find-by-name "attack_r")]
    (j/assoc! e :enabled true)
    (j/assoc-in! e [:sprite :opacity] 1)
    (js/setTimeout #(j/assoc-in! e [:sprite :opacity] 0.5) 75)
    (js/setTimeout #(j/assoc! e :enabled false) 150))


  (let [opacity #js {:opacity 1}
        last-opacity #js {:opacity 0}
        e (pc/find-by-name "attack_r")
        tween-opacity (-> (j/call e :tween opacity)
                        (j/call :to last-opacity 0.2 js/pc.Linear))
        _ (j/call tween-opacity :on "update"
            (fn []
              (j/assoc-in! e [:sprite :opacity] (j/get opacity :opacity))))]
    (j/call tween-opacity :start)
    nil)


  (let [color #js {:color 1}
        last-color #js {:color 0}
        e (pc/find-by-name "human_mage_mesh")
        tween-color (-> (j/call e :tween color)
                      (j/call :to last-color 1.5 js/pc.Linear)
                      ;(j/call :yoyo true)
                      ;(j/call :loop true)
                      ;(j/call :repeat )
                      )
        _ (j/call tween-color :on "update"
            (fn []
              (j/call-in e [:render :meshInstances 0 :setParameter] "material_emissive" #js[(j/get color :color) 0 0])))]
    (j/call tween-color :start)
    nil)

  (pc/set-loc-scale (pc/find-by-name "light_sprite2") 0.15)

  (let [e (pc/find-by-name "attack_r")]
    (j/assoc! e :enabled true)
    (j/assoc-in! e [:sprite :opacity] 1)
    (js/setTimeout #(j/assoc-in! e [:sprite :opacity] 0.5) 75)
    (js/setTimeout #(j/assoc! e :enabled false) 150))

  (pc/set-loc-pos (pc/find-by-name "light_sprite2") 0 -0.15 0)

  (j/call-in (pc/find-by-name "model_mesh") [:render :meshInstances 0 :setParameter] "material_emissive" #js[0.01 0 0])

  (j/call-in (pc/find-by-name "model_mesh") [:render :meshInstances 0 :setParameter] "material_opacity" 0.4)
  )
