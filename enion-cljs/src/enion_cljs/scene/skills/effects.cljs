(ns enion-cljs.scene.skills.effects
  (:require
    [applied-science.js-interop :as j]
    [enion-cljs.scene.pc :as pc]
    [enion-cljs.scene.states :refer [player other-players]])
  (:require-macros
    [enion-cljs.scene.macros :refer [fnt]]))

(defonce healed-player-ids (js/Set.))

(defn remove-player-id-from-healed-ids [id]
  (j/call healed-player-ids :delete (str id)))

(defn add-player-id-to-healed-ids [id]
  (let [id (str id)
        _ (j/call healed-player-ids :add (str id))
        _ (j/update-in! other-players [id :heal-counter] inc)
        new-counter (j/get-in other-players [id :heal-counter])]
    (js/setTimeout
      (fn []
        (when (= new-counter (j/get-in other-players [id :heal-counter]))
          (remove-player-id-from-healed-ids id)))
      2000)))

(defn remove-from-healed-ids []
  (j/call healed-player-ids :delete "-1"))

(defn add-to-healed-ids []
  (let [_ (j/call healed-player-ids :add "-1")
        _ (j/update! player :heal-counter inc)
        new-counter (j/get player :heal-counter)]
    (js/setTimeout
      (fn []
        (when (= new-counter (j/get player :heal-counter))
          (remove-from-healed-ids)))
      2000)))

;; TODO THESE ARE SINGLETONS, UPDATE , LIKE WE DID FOR NOVA!!!
(let [temp-final-scale #js {:x 0 :y 0.3 :z 0.3}]
  (defn- effect-scale-down [skill init-scale duration]
    (let [new-counter (-> skill (j/update! :counter inc) (j/get :counter))
          entity (j/get skill :entity)
          _ (j/assoc! entity :enabled true)
          _ (pc/set-loc-scale entity init-scale)
          first-scale (pc/get-loc-scale entity)
          tween-scale (-> (j/call entity :tween first-scale)
                          (j/call :to temp-final-scale duration js/pc.Linear))
          _ (j/call tween-scale :on "complete"
                    (fn []
                      (when (= new-counter (j/get skill :counter))
                        (j/assoc! entity :enabled false))))]
      (j/call tween-scale :start)
      nil)))

(let [last-opacity #js {:opacity 0}]
  (defn- effect-opacity-fade-out [skill duration]
    (println "a")
    (let [new-counter (-> skill (j/update! :counter inc) (j/get :counter))
          entity (j/get skill :entity)
          _ (js/console.log entity)
          _ (j/assoc! entity :enabled true)
          opacity #js {:opacity 1}
          tween-opacity (-> (j/call entity :tween opacity)
                            (j/call :to last-opacity duration js/pc.ExponentialIn))
          _ (j/call tween-opacity :on "update"
                    (fn []
                      (j/assoc-in! entity [:sprite :opacity] (j/get opacity :opacity))))
          _ (j/call tween-opacity :on "complete"
                    (fn []
                      (when (= new-counter (j/get skill :counter))
                        (j/assoc! entity :enabled false))))]
      (j/call tween-opacity :start)
      nil)))

(let [last-state #js {:particle 0}]
  (defn- effect-particle-fade-out [skill duration]
    (let [new-counter (-> skill (j/update! :counter inc) (j/get :counter))
          entity (j/get skill :entity)
          _ (j/assoc! entity :enabled true)
          par (j/get-in entity [:children 0 :particlesystem])
          _ (j/call par :reset)
          _ (j/call par :play)
          opacity #js {:particle 1}
          tween-particle (-> (j/call entity :tween opacity)
                             (j/call :to last-state duration js/pc.Linear))
          _ (j/call tween-particle :on "complete"
                    (fn []
                      (when (= new-counter (j/get skill :counter))
                        (j/assoc! entity :enabled false))))]
      (j/call tween-particle :start)
      nil)))

(defn apply-effect-attack-r [state]
  (effect-opacity-fade-out (j/get-in state [:effects :attack_r]) 0.2))

(defn apply-effect-attack-flame [state]
  (effect-opacity-fade-out (j/get-in state [:effects :attack_flame]) 1))

(defn apply-effect-attack-dagger [state]
  (effect-opacity-fade-out (j/get-in state [:effects :attack_dagger]) 0.5))

(defn apply-effect-attack-one-hand [state]
  (effect-opacity-fade-out (j/get-in state [:effects :attack_one_hand]) 0.65))

(defn apply-effect-attack-slow-down [state]
  (effect-scale-down (j/get-in state [:effects :attack_slow_down]) 0.3 0.2))

(defn apply-effect-attack-portal [state]
  (effect-scale-down (j/get-in state [:effects :portal]) 0.5 2))

(defn apply-effect-got-defense-break [state]
  (effect-particle-fade-out (j/get-in state [:effects :particle_got_defense_break]) 2))

(defn apply-effect-fire-hands [state]
  (effect-particle-fade-out (j/get-in state [:effects :particle_fire_hands]) 2))

(defn apply-effect-flame-particles [state]
  (effect-particle-fade-out (j/get-in state [:effects :particle_flame_dots]) 2.5))

(defn apply-effect-heal-particles [state]
  (effect-particle-fade-out (j/get-in state [:effects :particle_heal_hands]) 2))

(defn apply-effect-cure-particles [state]
  (effect-particle-fade-out (j/get-in state [:effects :particle_cure_hands]) 2))

(defn apply-effect-defense-break-particles [state]
  (effect-particle-fade-out (j/get-in state [:effects :particle_defense_break_hands]) 2.2))

(let [elapsed-time (volatile! 0)]
  (defn- update-fn [dt]
    (if-let [ids (seq healed-player-ids)]
      (let [et (vswap! elapsed-time + dt)
            _ (pc/set-elapsed-time-for-terrain et)
            positions (remove
                        nil?
                        (mapcat
                          (fn [id]
                            (when-let [e (if (= id "-1")
                                           (j/get player :entity)
                                           (j/get-in other-players [id :entity]))]
                              (let [pos (pc/get-pos e)
                                    x (j/get pos :x)
                                    z (j/get pos :z)]
                                [x z])))
                          ids))]
        (pc/set-heal-positions (/ (count positions) 2) positions))
      (pc/set-heal-positions))))

(defn init []
  (pc/create-script :effects
                    {:update (fnt (update-fn dt))}))

;; TODO maybe implement later on... (when health goes down below %35 the char gets red for a while)
#_(let [color #js {:color 1}
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
