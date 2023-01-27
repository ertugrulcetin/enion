(ns enion-cljs.scene.simulation
  (:require
    [applied-science.js-interop :as j]
    [enion-cljs.common :refer [fire on]]
    [enion-cljs.scene.pc :as pc]
    [enion-cljs.scene.states :as st]))

(defn spawn [player-id]
  (pc/set-anim-int (st/get-model-entity player-id) "health" 100)
  (st/enable-player-collision player-id)
  (st/set-health player-id 100))

(defn spawn-all []
  (doseq [id (->> (js/Object.keys st/other-players)
                  (filter
                    (fn [id]
                      (j/get (st/get-other-player id) :enemy?))))]
    (spawn id)))

(defn init []
  (fire :create-players
        [{:id 1
          :username "0000000"
          :health 100
          :race "human"
          :class "asas"
          :pos [38.39402389526367 0.550000011920929 -42.57197189331055]}
         {:id 2
          :username "Gandalf"
          :health 100
          :race "human"
          :class "mage"
          :pos [38.91624450683594 0.550000011920929 -40.86456298828125]}
         {:id 3
          :username "Orc_Warrior"
          :health 100
          :race "orc"
          :class "asas"
          :pos [38.5992431640625 0.550000011920929 -40.26069641113281]
          ;; :pos [(+ 38 (rand 1)) 0.55 (- (+ 39 (rand 4)))]
          }]))

(comment

  st/other-players
  (init)
  (st/destroy-players)
  )
