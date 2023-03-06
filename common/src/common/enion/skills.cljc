(ns common.enion.skills
  (:require
    [clojure.string :as str]))


(def close-attack-distance-threshold 0.75)
(def priest-skills-distance-threshold 8)
(def attack-range-distance-threshold 12)
(def attack-single-distance-threshold 9)

(def re-spawn-duration-in-milli-secs 5000)
(def party-request-duration-in-milli-secs 11000)


(defn rand-between
  [min max]
  (+ (Math/floor (* (Math/random) (+ (- max min) 1))) min))


(def classes
  {"warrior" {:health 1650
              :mana 1000}
   "asas" {:health 1400
           :mana 1200}
   "priest" {:health 1350
             :mana 1300}
   "mage" {:health 1200
           :mana 1500}})


(defn- create-damage-fn
  [start end]
  (fn [has-defense? got-break-defense?]
    (int
      (* (rand-between start end)
         (cond
           got-break-defense? 1.3
           has-defense? 0.85
           :else 1)))))


(def skills
  ;; Asas
  {"attackDagger" {:cooldown 750
                   :name "Twin Blades"
                   :description (str "Fast and deadly attack that allows the assassin "
                                     "to wield two daggers with deadly precision")
                   :required-mana 100
                   :damage-fn (create-damage-fn 225 300)}
   "phantomVision" {:cooldown 60000
                    :name "Phantom Vision"
                    :description (str "Allows the assassin and their party members to see invisible "
                                      "enemies who are using stealth or invisibility abilities")
                    :required-mana 200
                    :effect-duration (* 120 1000)}
   "hide" {:cooldown 30000
           :name "Ghost Step"
           :description (str "Stealthy movement ability that allows the assassin to move invisibly. "
                             "Assassin will become visible again after 50 seconds or when taking damage")
           :required-mana 150
           :effect-duration (* 50 1000)}
   ;; Warrior
   "attackOneHand" {:cooldown 950
                    :name "Savage Chop"
                    :description (str "Allows your character to unleash a powerful, precise strike "
                                      "with their weapon, dealing heavy damage to their enemies")
                    :required-mana 100
                    :damage-fn (create-damage-fn 300 450)}
   "attackSlowDown" {:cooldown 10000
                     :name "Slowing Slice"
                     :description (str "This skill has a 50% chance to apply a "
                                       "slowing effect on the target, reducing their movement "
                                       "speed for 5 seconds")
                     :required-mana 200
                     :damage-fn (create-damage-fn 100 200)
                     :effect-duration 5000}
   "shieldWall" {:cooldown (* 125 1000)
                 :name "Shield Wall"
                 :description "Increases the defense power of himself and all party members by 15% for 2 minutes"
                 :required-mana 200
                 :effect-duration (* 120 1000)}
   ;; Priest
   "heal" {:cooldown 2000
           :name "Healing Touch"
           :description "Restores 480 HP to your character or selected party member"
           :required-mana 200
           :hp 480}
   "breakDefense" {:cooldown 2500
                   :name "Toxic Spores"
                   :description (str "Unleashes a powerful burst of toxic energy, breaking the defenses "
                                     "of your enemies and increasing the damage they receive by 30% for 15 seconds.")
                   :required-mana 150
                   :effect-duration 15000}
   "cure" {:cooldown 2500
           :name "Purify"
           :description (str "Removes toxic effects from you and your party members, "
                             "restoring health and vitality")
           :required-mana 100}
   "attackPriest" {:cooldown 1000
                   :name "Divine Hammerstrike"
                   :damage-fn (create-damage-fn 100 200)
                   :description (str "Powerful close combat skill that allows the Priest to "
                                     "channel holy energy into their hammer, delivering a crushing blow to their enemies")
                   :required-mana 100}
   ;; Mage
   "attackRange" {:cooldown 15000
                  :name "Inferno Nova"
                  :description (str "Powerful fire-based attack that unleashes a burst "
                                    "of intense flames in a certain radius")
                  :required-mana 300
                  :damage-fn (create-damage-fn 300 400)}
   "attackSingle" {:cooldown 2000
                   :name "Flame Strike"
                   :description (str "Long range fire-based attack that unleashes a "
                                     "devastating blast of flame on a single enemy")
                   :required-mana 150
                   :damage-fn (create-damage-fn 150 200)}
   "attackIce" {:cooldown 4000
                :name "Frostfall"
                :description (str "Ice skill has a 20% chance of freezing enemies. "
                                  "Slows the target's movement speed for 2.5 seconds")
                :required-mana 200
                :damage-fn (create-damage-fn 100 175)
                :effect-duration 2500}
   "teleport" {:cooldown 1000
               :name "Teleport"
               :description "Teleports a single party member to your current location"
               :required-mana 100}
   ;; Common
   "attackR" {:required-mana 25
              :cooldown 200
              :damage-fn (create-damage-fn 20 50)}

   "fleetFoot" {:cooldown 26500
                :cooldown-asas 13500
                :name "Fleet Foot"
                :description (str "Increases your character's running speed by 30% for 25 seconds. "
                                  "(Assassins receive a 50% increase for 12 seconds)")
                :required-mana 50
                :effect-duration 25000
                :effect-duration-asas 12000}
   "hpPotion" {:cooldown 2000
               :name "HP Potion"
               :description "Restores 240 HP"
               :hp 240}
   "mpPotion" {:cooldown 2000
               :name "MP Potion"
               :description "Restores 360 MP"
               :mp 360}})


(defn username?
  [username]
  (and (string? username)
       (not (empty? username))
       (re-find #"^[a-zA-Z0-9_]{2,20}$" username)))


(defn random-pos-for-orc
  []
  [(+ 38 (rand 1)) 0.57 (- (+ 39 (rand 4)))])


(defn random-pos-for-human
  []
  [(- (+ 38 (rand 5.5))) 0.57 (+ 39 (rand 1.5))])
