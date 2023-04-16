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


(def classes
  {"warrior" {:health 1650
              :mana 1000}
   "asas" {:health 1400
           :mana 1200}
   "priest" {:health 1350
             :mana 1300}
   "mage" {:health 1200
           :mana 1500}})


(comment
  {:config {}
   :tutorials {}
   :asas {:level 1
          :exp 0
          :coins 0}}

  (take 60 (iterate
             (fn [x]
               (Math/round (+ x (Math/log (Math/pow x 2)))))
             100))

  (take 60 (iterate
             (fn [x]
               (Math/round (+ x (Math/exp (Math/log x)))))
             50))

  (defn calculate-exp [initial-exp num-levels]
  (loop [current-level 1
         exp initial-exp
         exps [initial-exp]]
    (if (> current-level num-levels)
      exps
      (let [base-multiplier 1.31415
            base-increment Math/PI
            increment-factor (* 0.1 current-level)
            next-exp (int (* exp base-multiplier))]
        (recur (inc current-level)
               (+ next-exp base-increment)
               (conj exps (Math/round (+ next-exp base-increment))))))))
  (map-indexed #(vector (inc %1) %2) (calculate-exp 120 30))

  (defn calculate-exp [initial-exp num-levels]
    (let [exp-seq (iterate #(Math/round (+ % (* 0.3 %))) initial-exp)]
      (take num-levels exp-seq)))

  (calculate-exp 100 30)

  )


(defn- create-damage-fn
  [start end]
  (fn [has-defense? got-break-defense?]
    (int
      (* (rand-between start end)
         (cond
           got-break-defense? 1.3
           has-defense? 0.85
           :else 1)))))


;; attacker-skill-damage-fn returns a random number with given range
(defn calculate-damage
  [player-defense-power
   attacker-attack-power
   attacker-skill-damage-fn
   damage-reduction]
  (let [skill-damage (attacker-skill-damage-fn)
        base-damage (* attacker-attack-power skill-damage)
        mitigated-damage (* player-defense-power damage-reduction)
        final-damage (max 0 (- base-damage mitigated-damage))]
    final-damage))


(comment
  (calculate-damage 10 25 (constantly 10) 0.4)
  )


(defn example-skill-damage-fn
  []
  (+ 1.0 (* 0.5 (rand))))


;; Sample player and attacker attributes
(def player-defense-power 50)
(def attacker-attack-power 100)


;; Sample damage reduction (0.4 or 40% reduction)
(def damage-reduction 0.4)


(comment
  (calculate-damage 300 attacker-attack-power example-skill-damage-fn damage-reduction))


(def skills
  ;; Asas
  {"attackDagger" {:cooldown 750
                   :name "Twin Blades"
                   :description (str "Fast and deadly attack that allows the assassin "
                                     "to wield two daggers with deadly precision")
                   :required-mana 100
                   :damage-fn (create-damage-fn 225 300)}
   "attackStab" {:cooldown 12000
                 :name "Dark Stab"
                 :description (str "A lethal close-range attack that lets the Assassin deliver a quick "
                                   "and deadly strike with a hidden blade")
                 :required-mana 200
                 :damage-fn (create-damage-fn 500 600)}
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
   "battleFury" {:cooldown (* 60 1000)
                 :name "Battle Fury"
                 :description "Increases attack damage by 10% for 5 seconds"
                 :required-mana 200
                 :effect-duration (* 5 1000)}
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
   "cure" {:cooldown 3500
           :name "Purify"
           :description (str "Removes toxic effects from you and your party members, "
                             "restoring health and vitality")
           :required-mana 100}
   "attackPriest" {:cooldown 1000
                   :name "Divine Hammerstrike"
                   :damage-fn (create-damage-fn 150 250)
                   :description (str "Powerful close combat skill that allows the Priest to "
                                     "channel holy energy into their hammer, delivering a crushing blow to their enemies")
                   :required-mana 100}
   ;; Mage
   "attackRange" {:cooldown 15000
                  :name "Inferno Nova"
                  :description (str "Powerful fire-based attack that unleashes a burst "
                                    "of intense flames in a certain radius")
                  :required-mana 300
                  :damage-fn (create-damage-fn 350 450)}
   "attackSingle" {:cooldown 2000
                   :name "Flame Strike"
                   :description (str "Long range fire-based attack that unleashes a "
                                     "devastating blast of flame on a single enemy")
                   :required-mana 150
                   :damage-fn (create-damage-fn 200 275)}
   "attackIce" {:cooldown 4000
                :name "Frostfall"
                :description (str "Ice skill has a 20% chance of freezing enemies. "
                                  "Slows the target's movement speed for 2.5 seconds")
                :required-mana 200
                :damage-fn (create-damage-fn 150 250)
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
                :cooldown-normal 26500
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
