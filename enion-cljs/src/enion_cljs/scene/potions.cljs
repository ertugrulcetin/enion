(ns enion-cljs.scene.potions
  (:require
    [applied-science.js-interop :as j]
    [enion-cljs.common :refer [fire on]]
    [enion-cljs.scene.states :as st]
    [enion-cljs.scene.utils :as utils]))

(defn update-potions [hp-potions mp-potions]
  (j/assoc! st/player
            :hp-potions hp-potions
            :mp-potions mp-potions)
  (utils/set-item "potions" (pr-str {:hp-potions hp-potions
                                     :mp-potions mp-potions}))
  (fire :ui-update-hp-potions hp-potions)
  (fire :ui-update-mp-potions mp-potions))

(on :rewarded-break-potions
    (fn []
      (update-potions 50 50)))
