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
  (+ (Math/floor (* (Math/random) (inc (- max min)))) min))


(def level->exp-table
  {1 100
   2 190
   3 342
   4 581
   5 929
   6 1393
   7 1950
   8 2535
   9 5070
   10 6084
   11 7300
   12 8760
   13 10512
   14 12612
   15 15136
   16 18163
   17 21795
   18 26134
   19 52308
   20 60154
   21 69177
   22 79553
   23 91485
   24 105207
   25 120988
   26 139139
   27 160006
   28 194006
   29 288012})


(def level->attack-power-table
  {1 90
   2 109
   3 118
   4 128
   5 138
   6 148
   7 158
   8 168
   9 178
   10 201
   11 212
   12 221
   13 230
   14 336
   15 342
   16 353
   17 364
   18 375
   19 386
   20 457
   21 508
   22 519
   23 531
   24 543
   25 555
   26 567
   27 579
   28 591
   29 603
   30 615})


(def level->health-mana-table
  {1 {"warrior" {:health 500 :mana 333} "asas" {:health 420 :mana 400} "priest" {:health 400 :mana 433} "mage" {:health 375 :mana 500}}
   2 {"warrior" {:health 725 :mana 500} "asas" {:health 600 :mana 600} "priest" {:health 575 :mana 650} "mage" {:health 500 :mana 750}}
   3 {"warrior" {:health 900 :mana 611} "asas" {:health 750 :mana 733} "priest" {:health 730 :mana 783} "mage" {:health 690 :mana 917}}
   4 {"warrior" {:health 1073 :mana 667} "asas" {:health 900 :mana 800} "priest" {:health 878 :mana 850} "mage" {:health 780 :mana 1000}}
   5 {"warrior" {:health 1155 :mana 722} "asas" {:health 967 :mana 867} "priest" {:health 945 :mana 917} "mage" {:health 840 :mana 1083}}
   6 {"warrior" {:health 1238 :mana 778} "asas" {:health 1033 :mana 933} "priest" {:health 1013 :mana 983} "mage" {:health 900 :mana 1167}}
   7 {"warrior" {:health 1320 :mana 833} "asas" {:health 1100 :mana 1000} "priest" {:health 1080 :mana 1050} "mage" {:health 960 :mana 1250}}
   8 {"warrior" {:health 1403 :mana 889} "asas" {:health 1167 :mana 1067} "priest" {:health 1148 :mana 1117} "mage" {:health 1020 :mana 1333}}
   9 {"warrior" {:health 1485 :mana 944} "asas" {:health 1233 :mana 1133} "priest" {:health 1215 :mana 1183} "mage" {:health 1080 :mana 1417}}
   10 {"warrior" {:health 1650 :mana 1000} "asas" {:health 1400 :mana 1200} "priest" {:health 1350 :mana 1300} "mage" {:health 1200 :mana 1500}}
   11 {"warrior" {:health 1732 :mana 1050} "asas" {:health 1470 :mana 1260} "priest" {:health 1417 :mana 1365} "mage" {:health 1260 :mana 1575}}
   12 {"warrior" {:health 1815 :mana 1100} "asas" {:health 1540 :mana 1320} "priest" {:health 1485 :mana 1430} "mage" {:health 1320 :mana 1650}}
   13 {"warrior" {:health 1897 :mana 1150} "asas" {:health 1610 :mana 1380} "priest" {:health 1553 :mana 1495} "mage" {:health 1380 :mana 1725}}
   14 {"warrior" {:health 1980 :mana 1200} "asas" {:health 1680 :mana 1440} "priest" {:health 1619 :mana 1560} "mage" {:health 1440 :mana 1800}}
   15 {"warrior" {:health 2475 :mana 1500} "asas" {:health 2100 :mana 1800} "priest" {:health 2025 :mana 1950} "mage" {:health 1800 :mana 2250}}
   16 {"warrior" {:health 2559 :mana 1538} "asas" {:health 2145 :mana 1830} "priest" {:health 2059 :mana 1975} "mage" {:health 1830 :mana 2288}}
   17 {"warrior" {:health 2643 :mana 1575} "asas" {:health 2190 :mana 1860} "priest" {:health 2093 :mana 2000} "mage" {:health 1860 :mana 2325}}
   18 {"warrior" {:health 2727 :mana 1613} "asas" {:health 2235 :mana 1890} "priest" {:health 2127 :mana 2025} "mage" {:health 1890 :mana 2363}}
   19 {"warrior" {:health 2811 :mana 1650} "asas" {:health 2280 :mana 1920} "priest" {:health 2161 :mana 2050} "mage" {:health 1920 :mana 2400}}
   20 {"warrior" {:health 2895 :mana 1688} "asas" {:health 2325 :mana 1950} "priest" {:health 2195 :mana 2075} "mage" {:health 1950 :mana 2438}}
   21 {"warrior" {:health 2979 :mana 1725} "asas" {:health 2370 :mana 1980} "priest" {:health 2229 :mana 2100} "mage" {:health 1980 :mana 2475}}
   22 {"warrior" {:health 3063 :mana 1763} "asas" {:health 2415 :mana 2010} "priest" {:health 2263 :mana 2125} "mage" {:health 2010 :mana 2513}}
   23 {"warrior" {:health 3147 :mana 1800} "asas" {:health 2460 :mana 2040} "priest" {:health 2297 :mana 2150} "mage" {:health 2040 :mana 2550}}
   24 {"warrior" {:health 3231 :mana 1838} "asas" {:health 2505 :mana 2070} "priest" {:health 2331 :mana 2175} "mage" {:health 2070 :mana 2588}}
   25 {"warrior" {:health 3315 :mana 1875} "asas" {:health 2550 :mana 2100} "priest" {:health 2365 :mana 2200} "mage" {:health 2100 :mana 2625}}
   26 {"warrior" {:health 3399 :mana 1913} "asas" {:health 2595 :mana 2130} "priest" {:health 2399 :mana 2225} "mage" {:health 2130 :mana 2663}}
   27 {"warrior" {:health 3483 :mana 1950} "asas" {:health 2640 :mana 2160} "priest" {:health 2433 :mana 2250} "mage" {:health 2160 :mana 2700}}
   28 {"warrior" {:health 3567 :mana 1988} "asas" {:health 2685 :mana 2190} "priest" {:health 2467 :mana 2275} "mage" {:health 2190 :mana 2738}}
   29 {"warrior" {:health 3651 :mana 2025} "asas" {:health 2730 :mana 2220} "priest" {:health 2501 :mana 2300} "mage" {:health 2220 :mana 2775}}
   30 {"warrior" {:health 3713 :mana 2250} "asas" {:health 3150 :mana 2700} "priest" {:health 3038 :mana 2925} "mage" {:health 2700 :mana 3375}}})


(comment
  ;; attack power
  (take 30 (iterate
             (fn [x]
               (Math/round (+ x (Math/log (Math/pow x 2)))))
             100)))


(defn random-double
  [start end]
  (let [range-size (+ end (- start))
        random-val (* (rand) range-size)]
    (+ start random-val)))


(defn- create-damage-fn
  ([damage-multiplier]
   (fn [has-defense? got-break-defense? attack-power]
     (let [damage-multiplier-range-constant (double (/ damage-multiplier 15))]
       (int
         (Math/abs
           (* attack-power
              (random-double (- damage-multiplier damage-multiplier-range-constant)
                             (+ damage-multiplier damage-multiplier-range-constant))
              (cond
                got-break-defense? 1.3
                has-defense? 0.85
                :else 1)))))))
  ([start end]
   (fn [has-defense? got-break-defense?]
     (int
       (* (rand-between start end)
          (cond
            got-break-defense? 1.3
            has-defense? 0.85
            :else 1))))))


(def skills
  ;; Asas
  {"attackDagger" {:cooldown 750
                   :name "Twin Blades"
                   :description (str "Fast and deadly attack that allows the assassin "
                                     "to wield two daggers with deadly precision")
                   :required-mana 50
                   :damage-fn (create-damage-fn 1.45)
                   :required-level 1}
   "attackStab" {:cooldown 12000
                 :name "Dark Stab"
                 :description (str "A lethal close-range attack that lets the Assassin deliver a quick "
                                   "and deadly strike with a hidden blade")
                 :required-mana 200
                 :damage-fn (create-damage-fn 2.55)
                 :required-level 7}
   "phantomVision" {:cooldown 60000
                    :name "Phantom Vision"
                    :description (str "Allows the assassin and their party members to see invisible "
                                      "enemies who are using stealth or invisibility abilities")
                    :required-mana 200
                    :effect-duration (* 120 1000)
                    :required-level 10}
   "hide" {:cooldown 30000
           :name "Ghost Step"
           :description (str "Stealthy movement ability that allows the assassin to move invisibly. "
                             "Assassin will become visible again after 50 seconds or when taking damage")
           :required-mana 150
           :effect-duration (* 50 1000)
           :required-level 5}
   ;; Warrior
   "attackOneHand" {:cooldown 950
                    :name "Savage Chop"
                    :description (str "Allows your character to unleash a powerful, precise strike "
                                      "with their weapon, dealing heavy damage to their enemies")
                    :required-mana 50
                    :damage-fn (create-damage-fn 1.75)
                    :required-level 1}
   "attackSlowDown" {:cooldown 12000
                     :name "Slowing Slice"
                     :description (str "This skill has a 50% chance to apply a "
                                       "slowing effect on the target, reducing their movement "
                                       "speed for 5 seconds")
                     :required-mana 200
                     :damage-fn (create-damage-fn 1.15)
                     :effect-duration 5000
                     :required-level 7}
   "shieldWall" {:cooldown (* 125 1000)
                 :name "Shield Wall"
                 :description "Increases the defense power of himself and all party members by 15% for 2 minutes"
                 :required-mana 200
                 :effect-duration (* 120 1000)
                 :required-level 5}
   "battleFury" {:cooldown (* 60 1000)
                 :name "Battle Fury"
                 :description "Increases attack damage by 10% for 5 seconds"
                 :required-mana 200
                 :effect-duration (* 5 1000)
                 :required-level 10}
   ;; Priest
   "heal" {:cooldown 2000
           :name "Healing Touch"
           :description "Restores 480 HP to your character or selected party member"
           :required-mana 200
           :hp 480
           :required-level 5}
   "breakDefense" {:cooldown 2500
                   :name "Toxic Spores"
                   :description (str "Unleashes a powerful burst of toxic energy, breaking the defenses "
                                     "of your enemies and increasing the damage they receive by 30% for 15 seconds.")
                   :required-mana 150
                   :effect-duration 15000
                   :required-level 7}
   "cure" {:cooldown 3500
           :name "Purify"
           :description (str "Removes toxic effects from you and your party members, "
                             "restoring health and vitality")
           :required-mana 100
           :required-level 10}
   "attackPriest" {:cooldown 1000
                   :name "Divine Hammerstrike"
                   :damage-fn (create-damage-fn 1.25)
                   :description (str "Powerful close combat skill that allows the Priest to "
                                     "channel holy energy into their hammer, delivering a crushing blow to their enemies")
                   :required-mana 50
                   :required-level 1}
   ;; Mage
   "attackRange" {:cooldown 15000
                  :name "Inferno Nova"
                  :description (str "Powerful fire-based attack that unleashes a burst "
                                    "of intense flames in a certain radius")
                  :required-mana 300
                  :damage-fn (create-damage-fn 2.15)
                  :required-level 10}
   "attackSingle" {:cooldown 1500
                   :name "Flame Strike"
                   :description (str "Long range fire-based attack that unleashes a "
                                     "devastating blast of flame on a single enemy")
                   :required-mana 50
                   :damage-fn (create-damage-fn 1.4)
                   :required-level 1}
   "attackIce" {:cooldown 5000
                :name "Frostfall"
                :description (str "Ice skill has a 20% chance of freezing enemies. "
                                  "Slows the target's movement speed for 2.5 seconds")
                :required-mana 200
                :damage-fn (create-damage-fn 1.75)
                :effect-duration 2500
                :required-level 5}
   "teleport" {:cooldown 1000
               :name "Teleport"
               :description "Teleports a single party member to your current location"
               :required-mana 100
               :required-level 7}
   ;; Common
   "attackR" {:required-mana 0
              :cooldown 200
              :damage-fn (create-damage-fn 0.25)
              :required-level 1}

   "fleetFoot" {:cooldown 26500
                :cooldown-normal 26500
                :cooldown-asas 13500
                :name "Fleet Foot"
                :description (str "Increases your character's running speed by 30% for 25 seconds. "
                                  "(Assassins receive a 50% increase for 12 seconds)")
                :required-mana 50
                :effect-duration 25000
                :effect-duration-asas 12000
                :required-level 3}
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
