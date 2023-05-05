(ns enion-backend.npc.type
  (:require
    [common.enion.npc :as common.npc]
    [enion-backend.utils :as utils]))

(def npc-params
  {:squid (merge
            (:squid common.npc/npcs)
            {:attack-range-threshold 1
             :change-pos-interval 8000
             :change-pos-speed 0.02
             :chase-range-threshold 20
             :chase-speed 0.1
             :cooldown 2000
             :damage-buffer-size 100
             :damage-fn #(utils/rand-between 10 30)
             :delay-after-last-time-attacked 1000
             :drop {:coin {:prob 0.9 :amount [10 20]}
                    :hp-potion {:prob 0.5 :amount 1}}
             :exp 60
             :target-locked-threshold 10000
             :target-pos-gap-threshold 0.2
             :re-spawn-interval 10000})
   :ghoul (merge
            (:ghoul common.npc/npcs)
            {:attack-range-threshold 1
             :change-pos-interval 8000
             :change-pos-speed 0.02
             :chase-range-threshold 20
             :chase-speed 0.05
             :cooldown 2000
             :damage-buffer-size 100
             :damage-fn #(utils/rand-between 25 55)
             :delay-after-last-time-attacked 2000
             :drop {:coin {:prob 0.9 :amount [20 45]}
                    :hp-potion {:prob 0.25 :amount 1}
                    :mp-potion {:prob 0.6 :amount 1}}
             :exp 150
             :target-locked-threshold 10000
             :target-pos-gap-threshold 0.2
             :re-spawn-interval 10000})
   :demon (merge
            (:demon common.npc/npcs)
            {:attack-range-threshold 1.25
             :change-pos-interval 7000
             :change-pos-speed 0.02
             :chase-range-threshold 15
             :chase-speed 0.06
             :cooldown 2000
             :damage-buffer-size 100
             :damage-fn #(utils/rand-between 55 95)
             :delay-after-last-time-attacked 2000
             :drop {:coin {:prob 0.9 :amount [50 100]}
                    :hp-potion {:prob 0.4 :amount [2 4]}
                    :mp-potion {:prob 0.3 :amount 2}}
             :exp 300
             :target-locked-threshold 10000
             :target-pos-gap-threshold 0.2
             :re-spawn-interval 16000})
   :skeleton-warrior (merge
                       (:skeleton-warrior common.npc/npcs)
                       {:attack-range-threshold 0.5
                        :attack-when-close-chase-range-threshold 3
                        :change-pos-interval 15000
                        :change-pos-speed 0.02
                        :chase-range-threshold 15
                        :chase-speed 0.12
                        :cooldown 2000
                        :damage-buffer-size 100
                        :damage-fn #(utils/rand-between 160 220)
                        :delay-after-last-time-attacked 1000
                        :drop {:coin {:prob 0.9 :amount [150 300]}
                               :hp-potion {:prob 0.5 :amount [2 5]}
                               :mp-potion {:prob 0.2 :amount 2}}
                        :exp 550
                        :target-locked-threshold 10000
                        :target-pos-gap-threshold 0.2
                        :re-spawn-interval 12000})
   :burning-skeleton (merge
                       (:burning-skeleton common.npc/npcs)
                       {:attack-range-threshold 0.5
                        :attack-when-close-chase-range-threshold 5
                        :change-pos-interval 15000
                        :change-pos-speed 0.02
                        :chase-range-threshold 20
                        :chase-speed 0.11
                        :cooldown 1500
                        :damage-buffer-size 150
                        :damage-fn #(utils/rand-between 240 350)
                        :delay-after-last-time-attacked 1250
                        :drop {:coin {:prob 0.9 :amount [300 500]}
                               :hp-potion {:prob 0.4 :amount 7}
                               :mp-potion {:prob 0.25 :amount 3}}
                        :exp 1150
                        :target-locked-threshold 10000
                        :target-pos-gap-threshold 0.2
                        :re-spawn-interval 14000})
   :gravestalker (merge
                   (:gravestalker common.npc/npcs)
                   {:attack-range-threshold 0.5
                    :change-pos-interval 42000
                    :change-pos-speed 0.005
                    :chase-range-threshold 15
                    :chase-speed 0.12
                    :cooldown 1000
                    :damage-buffer-size 100
                    :damage-fn #(utils/rand-between 190 240)
                    :delay-after-last-time-attacked 1000
                    :drop {:coin {:prob 0.9 :amount [650 900]}
                           :hp-potion {:prob 0.3 :amount 5}
                           :mp-potion {:prob 0.4 :amount 4}}
                    :exp 1500
                    :target-locked-threshold 10000
                    :target-pos-gap-threshold 0.2
                    :re-spawn-interval 22000})
   :skeleton-champion (merge
                        (:skeleton-champion common.npc/npcs)
                        {:attack-range-threshold 0.6
                         :attack-when-close-chase-range-threshold 5
                         :attack-when-close-range-threshold 0.3
                         :change-pos-interval 15000
                         :change-pos-speed 0.02
                         :chase-range-threshold 20
                         :chase-speed 0.09
                         :cooldown 2000
                         :damage-buffer-size 150
                         :damage-fn #(utils/rand-between 300 450)
                         :delay-after-last-time-attacked 1500
                         :drop {:coin {:prob 0.9 :amount [1200 1600]}
                                :mp-potion {:prob 0.4 :amount 7}}
                         :exp 2500
                         :target-locked-threshold 10000
                         :target-pos-gap-threshold 0.2
                         :re-spawn-interval 14000})
   :deruvish (merge
               (:deruvish common.npc/npcs)
               {:attack-range-threshold 5
                :change-pos-interval 24000
                :change-pos-speed 0.02
                :chase-range-threshold 25
                :chase-speed 0.07
                ;; :attack-when-close-range-threshold 2
                :cooldown 1200
                :damage-buffer-size 150
                :damage-fn #(utils/rand-between 300 400)
                :delay-after-last-time-attacked 1500
                :drop {:coin {:prob 0.9 :amount [1500 2100]}
                       :hp-potion {:prob 0.3 :amount 10}}
                :exp 3500
                :target-locked-threshold 10000
                :target-pos-gap-threshold 0.2
                :re-spawn-interval 21000})})
