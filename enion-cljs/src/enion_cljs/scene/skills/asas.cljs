(ns enion-cljs.scene.skills.asas
  (:require
    [applied-science.js-interop :as j]
    [enion-cljs.scene.keyboard :as k]
    [enion-cljs.scene.pc :as pc]
    [enion-cljs.scene.skills.core :as skills]
    [enion-cljs.scene.states :as st :refer [player]]
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

;; TODO hide olurken gelip birisi vurunca appear olunmali, appear hide asamasini iptal etmeli
;; su an hide olurken ortasinda appear gelince hide devam ediyor...
(defn create-hide-fn []
  (let [{:keys [race class model-entity]} (j/lookup player)
        initial-opacity #js {:opacity 1}
        last-opacity #js {:opacity 0.3}
        entity (pc/find-by-name model-entity (str race "_" class "_mesh"))]
    (fn []
      (j/assoc! player :hide? true)
      (j/assoc! initial-opacity :opacity (or (pc/get-mesh-opacity entity) 1))
      (let [tween-opacity (-> (j/call entity :tween initial-opacity)
                              (j/call :to last-opacity 2 pc/linear))
            _ (j/call tween-opacity :on "update"
                      (fn []
                        (if (j/get player :hide?)
                          (pc/set-mesh-opacity entity (j/get initial-opacity :opacity))
                          (j/call tween-opacity :stop))))]
        (j/call tween-opacity :start)
        nil))))

(defn create-appear-fn []
  (let [{:keys [race class model-entity]} (j/lookup player)
        initial-opacity #js {:opacity nil}
        last-opacity #js {:opacity 1}
        entity (pc/find-by-name model-entity (str race "_" class "_mesh"))]
    (fn []
      (j/assoc! player :hide? false)
      (j/assoc! initial-opacity :opacity (or (pc/get-mesh-opacity entity) 1))
      (let [tween-opacity (-> (j/call entity :tween initial-opacity)
                              (j/call :to last-opacity 0.3 pc/linear))
            _ (j/call tween-opacity :on "update"
                      (fn []
                        (pc/set-mesh-opacity entity (j/get initial-opacity :opacity))))]
        (j/call tween-opacity :start)
        nil))))

(defn create-hide-fn-other-player [other-player]
  (let [{:keys [race class model-entity enemy? template-entity]} (j/lookup other-player)
        initial-opacity #js {:opacity 1}
        last-opacity #js {:opacity (if enemy? 0 0.3)}
        mesh-lod-0 (pc/find-by-name model-entity (str race "_" class "_mesh_lod_0"))
        mesh-lod-1 (pc/find-by-name model-entity (str race "_" class "_mesh_lod_1"))
        mesh-lod-2 (pc/find-by-name model-entity (str race "_" class "_mesh_lod_2"))
        dagger-left (j/get-in (pc/find-by-name model-entity "asas_dagger_1") [:children 0])
        dagger-right (j/get-in (pc/find-by-name model-entity "asas_dagger_2") [:children 0])
        char-name-entity (pc/find-by-name template-entity "char_name")]
    (fn []
      (j/assoc! other-player :hide? true)
      (j/assoc! initial-opacity :opacity (or (pc/get-mesh-opacity mesh-lod-0) 1))
      (when enemy?
        (pc/disable dagger-right)
        (pc/disable dagger-left)
        (pc/disable char-name-entity))
      (let [tween-opacity (-> (j/call mesh-lod-0 :tween initial-opacity)
                              (j/call :to last-opacity 2 pc/linear))]
        (j/call tween-opacity :on "update"
                (fn []
                  (if (j/get other-player :hide?)
                    (doseq [e [mesh-lod-0 mesh-lod-1 mesh-lod-2]]
                      (pc/set-mesh-opacity e (j/get initial-opacity :opacity)))
                    (j/call tween-opacity :stop))))
        (when enemy?
          (j/call tween-opacity :on "complete"
                  (fn []
                    (if (j/get player :phantom-vision?)
                      (doseq [e [mesh-lod-0 mesh-lod-1 mesh-lod-2]]
                        (pc/set-mesh-opacity e 0.1))
                      (when (= (j/get other-player :id) (st/get-selected-player-id))
                        (st/cancel-selected-player))))))
        (j/call tween-opacity :start)
        nil))))

(defn create-appear-fn-other-player [other-player]
  (let [{:keys [race class model-entity enemy? template-entity]} (j/lookup other-player)
        initial-opacity #js {:opacity nil}
        last-opacity #js {:opacity 1}
        mesh-lod-0 (pc/find-by-name model-entity (str race "_" class "_mesh_lod_0"))
        mesh-lod-1 (pc/find-by-name model-entity (str race "_" class "_mesh_lod_1"))
        mesh-lod-2 (pc/find-by-name model-entity (str race "_" class "_mesh_lod_2"))
        dagger-left (j/get-in (pc/find-by-name model-entity "asas_dagger_1") [:children 0])
        dagger-right (j/get-in (pc/find-by-name model-entity "asas_dagger_2") [:children 0])
        char-name-entity (pc/find-by-name template-entity "char_name")]
    (fn []
      (j/assoc! other-player :hide? false)
      (j/assoc! initial-opacity :opacity (or (pc/get-mesh-opacity mesh-lod-0) 1))
      (when enemy?
        (pc/enable dagger-right)
        (pc/enable dagger-left)
        (pc/enable char-name-entity))
      (let [tween-opacity (-> (j/call mesh-lod-0 :tween initial-opacity)
                              (j/call :to last-opacity 0.3 pc/linear))]
        (j/call tween-opacity :on "update"
                (fn []
                  (doseq [e [mesh-lod-0 mesh-lod-1 mesh-lod-2]]
                    (pc/set-mesh-opacity e (j/get initial-opacity :opacity)))))
        (j/call tween-opacity :start)
        nil))))

(defn process-skills [e]
  (when-not (-> e .-event .-repeat)
    (let [model-entity (st/get-model-entity)
          active-state (pc/get-anim-state model-entity)]
      (m/process-cancellable-skills
        ["attackDagger" "attackR" "hide"]
        (j/get-in e [:event :code])
        active-state
        player)
      (cond
        (and (= active-state "attackDagger")
             (skills/skill-pressed? e "attackR")
             (j/get player :can-r-attack-interrupt?))
        (do
          (println "R combo!")
          (pc/set-anim-boolean model-entity "attackDagger" false)
          (pc/set-anim-boolean model-entity "attackR" true))

        (and (= active-state "attackR")
             (skills/skill-pressed? e "attackDagger")
             (j/get player :can-r-attack-interrupt?)
             (> (- (js/Date.now) @last-one-hand-combo) (utils/rand-between 750 1200)))
        (do
          (println "dagger combo...!")
          (pc/set-anim-boolean model-entity "attackR" false)
          (pc/set-anim-boolean model-entity "attackDagger" true)
          (reset! last-one-hand-combo (js/Date.now)))

        (and (= "idle" active-state) (k/pressing-wasd?))
        (pc/set-anim-boolean model-entity "run" true)

        (and (skills/idle-run-states active-state) (pc/key? e :KEY_SPACE) (j/get player :on-ground?))
        (pc/set-anim-boolean model-entity "jump" true)

        (and (skills/idle-run-states active-state) (skills/skill-pressed? e "attackDagger"))
        (pc/set-anim-boolean model-entity "attackDagger" true)

        (and (skills/idle-run-states active-state) (skills/skill-pressed? e "hide"))
        (pc/set-anim-boolean model-entity "hide" true)

        (and (skills/idle-run-states active-state) (skills/skill-pressed? e "attackR"))
        (pc/set-anim-boolean model-entity "attackR" true)))))
