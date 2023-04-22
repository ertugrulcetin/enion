(ns enion-backend.npc.drop
  (:require
    [enion-backend.utils :as utils]))

(def drops
  {:coin (fn [{:keys [prob amount-range]}]
           (when (utils/prob? prob)
             (utils/rand-between (first amount-range) (second amount-range))))})
