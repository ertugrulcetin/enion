(ns enion-cljs.common
  (:require
    [applied-science.js-interop :as j]
    [clojure.string :as str]
    [common.enion.skills :as common.skills]))

(defonce app nil)
(defonce state (clj->js {}))

(defonce global-on-listeners (atom []))

(def dev?
  ^boolean goog.DEBUG)

(def skill-slot-order-by-class
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

(defn update-fleet-foot-cooldown-for-asas []
  (let [duration (-> common.skills/skills (get "fleetFoot") :cooldown-asas)]
    (set! common.skills/skills (update-in common.skills/skills ["fleetFoot" :cooldown] (constantly duration)))))

(defn set-app [app*]
  (set! app app*))

(defn fire
  ([event]
   (j/call app :fire (name event)))
  ([event x]
   (j/call app :fire (name event) x))
  ([event x y]
   (j/call app :fire (name event) x y))
  ([event x y z]
   (j/call app :fire (name event) x y z)))

(defn- on* [event f]
  (when (j/call app :hasEvent (name event))
    (j/call app :off (name event)))
  (j/call app :on (name event) f))

(defn on [event f]
  (if app
    (on* event f)
    (swap! global-on-listeners conj #(on* event f))))

(defn enable-global-on-listeners []
  (doseq [f @global-on-listeners]
    (f))
  (reset! global-on-listeners []))

(defn dlog [& args]
  (when dev?
    (if (= 1 (count args))
      (js/console.log (first args))
      (js/console.log (str/join " " args)))))
