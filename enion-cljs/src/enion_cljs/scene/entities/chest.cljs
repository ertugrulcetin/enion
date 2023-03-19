(ns enion-cljs.scene.entities.chest
  (:require
    [applied-science.js-interop :as j]
    [enion-cljs.common :refer [fire]]
    [enion-cljs.scene.pc :as pc]
    [enion-cljs.scene.potions :as potions]
    [enion-cljs.scene.states :as st]
    [enion-cljs.scene.utils :as utils]))

(defn- trigger-start [chest]
  (let [earned-potions-count 50]
    (j/assoc! chest :enabled false)
    (potions/update-potions earned-potions-count earned-potions-count)
    (fire :ui-show-congrats-text earned-potions-count)
    (st/play-sound "success")
    (utils/finish-tutorial-step :what-is-the-first-quest?)))

(defn register-chest-trigger-events [chest-tutorial-not-done?]
  (when chest-tutorial-not-done?
    (let [chest (pc/find-by-name "chest")]
      (j/assoc! chest :enabled true)
      (j/call-in chest [:collision :on] "triggerenter" (fn [] (trigger-start chest))))))
