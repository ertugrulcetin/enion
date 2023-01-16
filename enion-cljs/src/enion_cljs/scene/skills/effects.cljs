(ns enion-cljs.scene.skills.effects
  (:require
    [applied-science.js-interop :as j]
    [enion-cljs.scene.pc :as pc]))

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
    (let [new-counter (-> skill (j/update! :counter inc) (j/get :counter))
          entity (j/get skill :entity)
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
