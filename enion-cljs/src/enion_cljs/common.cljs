(ns enion-cljs.common
  (:require
    [applied-science.js-interop :as j]))

(defonce app nil)
(defonce state (clj->js {}))

(def skills
  {:asas {1 "attackDagger"
          2 "phantomVision"
          3 "hide"
          4 "fleetFoot"
          5 "hpPotion"
          6 "mpPotion"}
   :warrior {1 "attackOneHand"
             2 "attackSlowDown"
             3 "shieldWall"
             4 "fleetFoot"
             5 "hpPotion"
             6 "mpPotion"}
   :mage {1 "attackRange"
          2 "attackSingle"
          3 "teleport"
          4 "fleetFoot"
          5 "hpPotion"
          6 "mpPotion"}
   :priest {1 "heal"
            2 "breakDefense"
            3 "cure"
            4 "fleetFoot"
            5 "hpPotion"
            6 "mpPotion"}})

(defn set-app [app*]
  (set! app app*))

(defn fire
  ([event x]
   (j/call app :fire (name event) x))
  ([event x y]
   (j/call app :fire (name event) x y))
  ([event x y z]
   (j/call app :fire (name event) x y z)))

(defn on [event f]
  (when (j/call app :hasEvent (name event))
    (j/call app :off (name event)))
  (j/call app :on (name event) f))
