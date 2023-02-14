(ns enion-cljs.scene.skills.asas
  (:require
    [applied-science.js-interop :as j]
    [common.enion.skills :as common.skills]
    [enion-cljs.common :refer [fire on dlog]]
    [enion-cljs.scene.network :as net :refer [dispatch-pro]]
    [enion-cljs.scene.pc :as pc]
    [enion-cljs.scene.skills.core :as skills]
    [enion-cljs.scene.skills.effects :as skills.effects]
    [enion-cljs.scene.states :as st :refer [player]]
    [enion-cljs.scene.utils :as utils])
  (:require-macros
    [enion-cljs.scene.macros :as m]))

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

(def attack-dagger-cooldown (-> common.skills/skills (get "attackDagger") :cooldown))
(def attack-dagger-required-mana (-> common.skills/skills (get "attackDagger") :required-mana))

(def last-one-hand-combo (volatile! (js/Date.now)))

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

(defmethod skills/skill-response "attackDagger" [params]
  (fire :ui-cooldown "attackDagger")
  (let [selected-player-id (-> params :skill :selected-player-id)
        damage (-> params :skill :damage)
        enemy (st/get-other-player selected-player-id)]
    (skills.effects/apply-effect-attack-dagger enemy)
    (fire :ui-send-msg {:to (j/get (st/get-other-player selected-player-id) :username)
                        :hit damage})))

(defmethod skills/skill-response "phantomVision" [_]
  (fire :ui-cooldown "phantomVision")
  (skills.effects/apply-effect-phantom-vision player)
  (skills/enable-phantom-vision)
  (st/play-sound "pv-sw"))

(defmethod skills/skill-response "hide" [_]
  (fire :ui-cooldown "hide")
  (j/call-in player [:skills :hide]))

(defmethod net/dispatch-pro-response :hide-finished [_]
  (j/call-in player [:skills :appear]))

(defn- dagger-combo? [e active-state selected-player-id]
  (and (= active-state "attackR")
       (skills/skill-pressed? e "attackDagger")
       (j/get player :can-r-attack-interrupt?)
       (> (- (js/Date.now) @last-one-hand-combo) (utils/rand-between
                                                   attack-dagger-cooldown
                                                   (+ attack-dagger-cooldown 400)))
       (st/cooldown-ready? "attackDagger")
       (st/enemy-selected? selected-player-id)
       (st/alive? selected-player-id)
       (st/enough-mana? attack-dagger-required-mana)
       (skills/close-for-attack? selected-player-id)))

;; create a function like attack-one-hand? but for attackDagger skill
(defn- attack-dagger? [e active-state selected-player-id]
  (and
    (skills/idle-run-states active-state)
    (skills/skill-pressed? e "attackDagger")
    (st/cooldown-ready? "attackDagger")
    (st/enemy-selected? selected-player-id)
    (st/alive? selected-player-id)
    (st/enough-mana? attack-dagger-required-mana)
    (skills/close-for-attack? selected-player-id)))

(def phantom-vision-required-mana (-> common.skills/skills (get "phantomVision") :required-mana))
(def hide-required-mana (-> common.skills/skills (get "hide") :required-mana))

(defn- phantom-vision? [e]
  (and
    (skills/skill-pressed? e "phantomVision")
    (st/cooldown-ready? "phantomVision")
    (st/enough-mana? phantom-vision-required-mana)))

(defn- hide? [e active-state]
  (and (skills/idle-run-states active-state)
       (skills/skill-pressed? e "hide")
       (st/cooldown-ready? "hide")
       (st/enough-mana? hide-required-mana)))

(defn process-skills [e]
  (when (and (not (-> e .-event .-repeat)) (st/alive?))
    (let [model-entity (st/get-model-entity)
          active-state (pc/get-anim-state model-entity)
          selected-player-id (st/get-selected-player-id)]
      (m/process-cancellable-skills
        ["attackDagger" "attackR" "hide"]
        (j/get-in e [:event :code])
        active-state
        player)
      (cond
        (skills/r-combo? e "attackDagger" active-state selected-player-id)
        (do
          (dlog "R combo!")
          (pc/set-anim-boolean model-entity "attackDagger" false)
          (pc/set-anim-boolean model-entity "attackR" true))

        (dagger-combo? e active-state selected-player-id)
        (do
          (println "dagger combo...!")
          (pc/set-anim-boolean model-entity "attackR" false)
          (pc/set-anim-boolean model-entity "attackDagger" true)
          (vreset! last-one-hand-combo (js/Date.now))
          (st/play-sound "attackDagger"))

        (skills/run? active-state)
        (pc/set-anim-boolean model-entity "run" true)

        (skills/jump? e active-state)
        (pc/set-anim-boolean model-entity "jump" true)

        (attack-dagger? e active-state selected-player-id)
        (do
          (j/assoc-in! player [:skill->selected-player-id "attackDagger"] selected-player-id)
          (pc/set-anim-boolean (st/get-model-entity) "attackDagger" true)
          (st/play-sound "attackDagger"))

        (hide? e active-state)
        (do
          (pc/set-anim-boolean model-entity "hide" true)
          (st/play-sound "hide"))

        (skills/attack-r? e active-state selected-player-id)
        (do
          (j/assoc-in! player [:skill->selected-player-id "attackR"] selected-player-id)
          (pc/set-anim-boolean model-entity "attackR" true)
          (st/look-at-selected-player))

        (phantom-vision? e)
        (dispatch-pro :skill {:skill "phantomVision"})

        (skills/fleet-foot? e)
        (dispatch-pro :skill {:skill "fleetFoot"})

        (skills/hp-potion? e)
        (dispatch-pro :skill {:skill "hpPotion"})

        (skills/mp-potion? e)
        (dispatch-pro :skill {:skill "mpPotion"})))))
