(ns enion-cljs.common
  (:require
    [applied-science.js-interop :as j]))

(defonce app nil)
(defonce state (clj->js {}))

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

(def skills
  {"attackDagger" {:cooldown 750
                   :name "Twin Blades"
                   :description (str "Fast and deadly attack that allows the assassin "
                                     "to wield two daggers with deadly precision.")}
   "phantomVision" {:cooldown 30000
                    :name "Phantom Vision"
                    :description (str "Allows the assassin and their party members to see invisible "
                                      "enemies who are using stealth or invisibility abilities.")}
   "hide" {:cooldown 60000
           :name "Ghost Step"
           :description (str "Stealthy movement ability that allows the assassin to move invisibly "
                             "while running. However, Ghost Step does have a limited duration, and the assassin "
                             "will become visible again after 50 seconds or when taking damage.")}
   "attackOneHand" {:cooldown 750
                    :name "Savage Chop"
                    :description (str "Allows your character to unleash a powerful, precise strike "
                                      "with their weapon, dealing heavy damage to their enemies.")}
   "attackSlowDown" {:cooldown 10000
                     :name "Slowing Slice"
                     :description (str "Attack that targets the legs of an enemy, causing them to slow "
                                       "down and become less mobile. This skill has a 50% chance to apply a "
                                       "slowing effect on the target, reducing their movement "
                                       "speed for a short duration.")}
   "shieldWall" {:cooldown 50000
                 :name "Shield Wall"
                 :description (str "Defensive ability that allows the warrior to enhance the "
                                   "defenses of himself and all party members. Increases the defense power of "
                                   "affected characters by 15% for a short duration.")}
   "heal" {:cooldown 2000
           :name "Healing Touch"
           :description "Restores 240 HP to your character and their party members."}
   "breakDefense" {:cooldown 5000
                   :name "Toxic Spores"
                   :description (str "Unleashes a powerful burst of toxic energy, breaking the defenses "
                                     "of your enemies and leaving them vulnerable for 15 seconds.")}
   "cure" {:cooldown 7000
           :name "Purify"
           :description (str "Removes toxic effects from you and your party members, "
                             "restoring health and vitality.")}
   "attackRange" {:cooldown 20000
                  :name "Inferno Nova"
                  :description (str "Powerful fire-based attack that unleashes a burst "
                                    "of intense flames in a certain radius.")}
   "attackSingle" {:cooldown 4000
                   :name "Flame Strike"
                   :description (str "Long range fire-based attack that unleashes a "
                                     "devastating blast of flame on a single enemy.")}
   "teleport" {:cooldown 1000
               :name "Teleport"
               :description (str "Powerful arcane ability that allows the mage to instantly transport a "
                                 "single party member to the same location as the mage.")}
   "fleetFoot" {:cooldown 15000
                :name "Fleet Foot"
                :description (str "Increases your character's running speed by 20%. Whether you're "
                                  "trying to outmaneuver your enemies or make a quick escape, this skill gives "
                                  "you the speed and mobility you need.")}
   "hpPotion" {:cooldown 1500
               :name "HP Potion"
               :description "Restores 240 HP."}
   "mpPotion" {:cooldown 1500
               :name "MP Potion"
               :description "Restores 240 MP."}})

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
