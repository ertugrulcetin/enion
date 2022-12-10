(ns enion-cljs.scene.skills.mage
  (:require
    [applied-science.js-interop :as j]
    [enion-cljs.scene.pc :as pc]))

;; TODO check temp-final-pos, when there are multiple novas, what to do? override may happen...
(let [temp-final-pos (js-obj)
      temp-final-scale #js {:x 5 :y 5 :z 5}
      opacity #js {:opacity 0}]
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
          mat (j/get-in entity [:render :meshInstances 0 :material])
          _ (do
              (j/assoc! mat :opacity 0.75)
              (j/call mat :update))
          tween-opacity (-> (j/call entity :tween mat)
                            (j/call :to opacity 1 js/pc.Linear)
                            (j/call :delay 0.5))
          _ (j/call tween-opacity :on "update" (fn []
                                                 (j/call mat :update)))
          _ (j/call tween-opacity :on "complete" (fn []
                                                   (j/call-in entity [:children 0 :particlesystem :stop])
                                                   (j/call-in entity [:children 0 :particlesystem :reset])
                                                   (j/assoc! entity :enabled false)))]
      (j/call tween-loc :start)
      (j/call tween-scale :start)
      (j/call tween-opacity :start)
      (j/call-in entity [:children 0 :particlesystem :play])
      nil)))

(comment
  (pc/set-loc-scale (pc/find-by-name "nova") 1)
  (pc/get-loc-scale (pc/find-by-name "nova")))
