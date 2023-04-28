(ns enion-backend.npc.drop
  (:require
    [enion-backend.utils :as utils]))

(defn get-drops [drops]
  (reduce-kv
    (fn [acc k {:keys [prob amount]}]
      (if (utils/prob? prob)
        (assoc acc k (if (vector? amount)
                       (utils/rand-between (first amount) (second amount))
                       (utils/rand-between 1 amount)))
        acc))
    {}
    drops))
