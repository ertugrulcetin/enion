(ns enion-cljs.common
  (:require
    [applied-science.js-interop :as j]
    [clojure.string :as str]
    [common.enion.skills :as common.skills]))

(defonce app nil)
(defonce state (clj->js {}))

(defonce global-on-listeners (atom []))

(goog-define ws-url "ws://localhost:3000/ws")

(def dev?
  ^boolean goog.DEBUG)

(def skill-slot-order-by-class
  {:asas {1 "attackDagger"
          2 "attackStab"
          3 "phantomVision"
          4 "hide"
          5 "fleetFoot"
          6 "hpPotion"
          7 "mpPotion"}
   :warrior {1 "attackOneHand"
             2 "attackSlowDown"
             3 "shieldWall"
             4 "battleFury"
             5 "fleetFoot"
             6 "hpPotion"
             7 "mpPotion"}
   :mage {1 "attackRange"
          2 "attackSingle"
          3 "attackIce"
          4 "teleport"
          5 "fleetFoot"
          6 "hpPotion"
          7 "mpPotion"}
   :priest {1 "heal"
            2 "breakDefense"
            3 "attackPriest"
            4 "cure"
            5 "fleetFoot"
            6 "hpPotion"
            7 "mpPotion"}})

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
