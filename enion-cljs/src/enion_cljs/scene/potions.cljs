(ns enion-cljs.scene.potions
  (:require
    [enion-cljs.common :refer [fire on]]
    [enion-cljs.scene.drop :as drop]))

(on :rewarded-break-potions
    (fn []
      (drop/inc-potion :hp 25)
      (drop/inc-potion :mp 25)))
