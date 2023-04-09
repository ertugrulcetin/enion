(ns enion-cljs.scene.entities.chest
  (:require
    [applied-science.js-interop :as j]
    [enion-cljs.common :refer [fire]]
    [enion-cljs.scene.drop :as drop]
    [enion-cljs.scene.pc :as pc]
    [enion-cljs.scene.states :as st]
    [enion-cljs.scene.utils :as utils]))

(defn- trigger-start [chest]
  (let [earned-potions-count 25]
    (j/assoc! chest :enabled false)
    (drop/inc-potion :hp earned-potions-count)
    (drop/inc-potion :mp earned-potions-count)
    (fire :ui-show-congrats-text earned-potions-count)
    (st/play-sound "success")
    (utils/finish-tutorial-step :what-is-the-first-quest?)))

(defn register-chest-trigger-events [chest-tutorial-not-done?]
  (when chest-tutorial-not-done?
    (let [chest (pc/find-by-name "chest")]
      (j/assoc! chest :enabled true)
      (j/call-in chest [:collision :on] "triggerenter" (fn [] (trigger-start chest))))))
