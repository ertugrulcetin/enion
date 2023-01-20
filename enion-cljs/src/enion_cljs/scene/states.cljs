(ns enion-cljs.scene.states
  (:require [enion-cljs.scene.pc :as pc]))

(defonce player (clj->js {:speed 550
                          :x 0
                          :z 0
                          :target-y nil
                          :eulers (pc/vec3)
                          :temp-dir (pc/vec3)
                          :world-dir (pc/vec3)
                          :mouse-left-locked? false
                          :target-pos (pc/vec3)
                          :target-pos-available? false
                          :skill-locked? false
                          :can-r-attack-interrupt? false
                          :ray (pc/ray)
                          :hit-position (pc/vec3)}))
(defonce other-players #js {})
(defonce settings #js #{})
