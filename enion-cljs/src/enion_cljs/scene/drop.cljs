(ns enion-cljs.scene.drop
  (:require
    [applied-science.js-interop :as j]
    [enion-cljs.common :refer [on fire]]
    [enion-cljs.scene.states :as st]
    [enion-cljs.scene.utils :as utils]))

(defn inc-potion [type n]
  (if (= type :hp)
    (let [hp-potions (j/get (j/update! st/player :hp-potions + n) :hp-potions)]
      (fire :ui-update-hp-potions hp-potions)
      (utils/set-item "potions" (pr-str {:hp-potions hp-potions
                                         :mp-potions (j/get st/player :mp-potions)})))
    (let [mp-potions (j/get (j/update! st/player :mp-potions + n) :mp-potions)]
      (fire :ui-update-mp-potions mp-potions)
      (utils/set-item "potions" (pr-str {:hp-potions (j/get st/player :hp-potions)
                                         :mp-potions mp-potions})))))

(on :rewarded-break-potions
    (fn []
      (inc-potion :hp 25)
      (inc-potion :mp 25)))
