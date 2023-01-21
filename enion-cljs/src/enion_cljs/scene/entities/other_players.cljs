(ns enion-cljs.scene.entities.other-players
  (:require
    [applied-science.js-interop :as j]
    [enion-cljs.scene.pc :as pc]
    [enion-cljs.scene.states :refer [other-players]])
  (:require-macros
    [enion-cljs.scene.macros :refer [fnt]]))

#_(defn get-hidden-enemy-asases []
  (reduce
    (fn [acc id]
      (let [other-player (j/get other-players id)]
        (js/console.log other-player)
        (if (and (j/get other-player :enemy?) (j/get other-player :hide?))
          (conj acc other-player)
          acc)))
    [] (js/Object.keys other-players)))

(comment
  (get-hidden-enemy-asases))

(defn- init-fn [_])

(defn init []
  (pc/create-script :other-players
                    {:init (fnt (init-fn this))}))

(comment
  (add-player-id-to-healed 111)
  (remove-player-id-from-healed 12)

  (js/console.log healed-player-ids)
  )
