(ns enion-cljs.scene.states
  (:require
    [applied-science.js-interop :as j]
    [enion-cljs.scene.pc :as pc]))

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
(defonce settings #js {})

(defn get-player-entity []
  (j/get player :entity))

(defn get-model-entity []
  (j/get player :model-entity))

(defn destroy [player-id]
  (j/call-in other-players [player-id :entity :destroy])
  (js-delete other-players player-id))
