(ns common.enion.skills
  (:require [clojure.string :as str]))

(def close-attack-distance-threshold 0.75)

(defn rand-between [min max]
  (+ (Math/floor (* (Math/random) (+ (- max min) 1))) min))

(def classes
  {"warrior" {:health 1600
              :mana 1000}
   "asas" {:health 1400
           :mana 1200}
   "priest" {:health 1350
             :mana 1300}
   "mage" {:health 1200
           :mana 1500}})

(def skills
  ;;Asas
  {"attackDagger" {:cooldown 750
                   :name "Twin Blades"
                   :description (str "Fast and deadly attack that allows the assassin "
                                  "to wield two daggers with deadly precision")
                   :required-mana 100
                   :damage-fn #(rand-between 150 250)}
   "phantomVision" {:cooldown 30000
                    :name "Phantom Vision"
                    :description (str "Allows the assassin and their party members to see invisible "
                                   "enemies who are using stealth or invisibility abilities")
                    :required-mana 200}
   "hide" {:cooldown 15000
           :name "Ghost Step"
           :description (str "Stealthy movement ability that allows the assassin to move invisibly "
                          "while running. However, Ghost Step does have a limited duration, and the assassin "
                          "will become visible again after 50 seconds or when taking damage")
           :required-mana 150}
   ;;Warrior
   "attackOneHand" {:cooldown 950
                    :name "Savage Chop"
                    :description (str "Allows your character to unleash a powerful, precise strike "
                                   "with their weapon, dealing heavy damage to their enemies")
                    :required-mana 100
                    :damage-fn #(rand-between 200 300)}
   "attackSlowDown" {:cooldown 10000
                     :name "Slowing Slice"
                     :description (str "This skill has a 50% chance to apply a "
                                    "slowing effect on the target, reducing their movement "
                                    "speed for a short duration")
                     :required-mana 200
                     :damage-fn #(rand-between 100 200)}
   "shieldWall" {:cooldown 50000
                 :name "Shield Wall"
                 :description "Increases the defense power of himself and all party members by 15% for a short duration"
                 :required-mana 200}
   ;;Priest
   "heal" {:cooldown 2000
           :name "Healing Touch"
           :description "Restores 480 HP to your character or selected party member"
           :required-mana 200}
   "breakDefense" {:cooldown 1500
                   :name "Toxic Spores"
                   :description (str "Unleashes a powerful burst of toxic energy, breaking the defenses "
                                  "of your enemies and leaving them vulnerable for 15 seconds")
                   :required-mana 150}
   "cure" {:cooldown 1500
           :name "Purify"
           :description (str "Removes toxic effects from you and your party members, "
                          "restoring health and vitality")
           :required-mana 100}
   ;;Mage
   "attackRange" {:cooldown 15000
                  :name "Inferno Nova"
                  :description (str "Powerful fire-based attack that unleashes a burst "
                                 "of intense flames in a certain radius")
                  :required-mana 300
                  :damage-fn #(rand-between 300 400)}
   "attackSingle" {:cooldown 3000
                   :name "Flame Strike"
                   :description (str "Long range fire-based attack that unleashes a "
                                  "devastating blast of flame on a single enemy")
                   :required-mana 100
                   :damage-fn #(rand-between 100 150)}
   "teleport" {:cooldown 500
               :name "Teleport"
               :description (str "Powerful arcane ability that allows the mage to instantly transport a "
                              "single party member to the same location as the mage")
               :required-mana 50}
   ;;Common
   "attackR" {:required-mana 25
              :cooldown 200
              :damage-fn #(rand-between 20 50)}

   ;;TODO cooldown is different for asas, handle in the backend!
   "fleetFoot" {:cooldown 26000
                :name "Fleet Foot"
                :description (str "Increases your character's running speed by 30% for 25 seconds. "
                               "(Assassins receive a 50% increase for 12 seconds)")
                :required-mana 50}
   "hpPotion" {:cooldown 1500
               :name "HP Potion"
               :description "Restores 240 HP"
               :hp 240}
   "mpPotion" {:cooldown 1500
               :name "MP Potion"
               :description "Restores 360 MP"
               :mp 360}})

