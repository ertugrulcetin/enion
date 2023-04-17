(ns enion-backend.npc.type
  (:require
    [common.enion.npc :as common.npc]
    [enion-backend.utils :as utils]))

(def npc-params
  {:skeleton-warrior (merge
                       (:skeleton-warrior common.npc/npcs)
                       {:attack-range-threshold 0.5
                        :change-pos-interval 15000
                        :change-pos-speed 0.02
                        :chase-range-threshold 15
                        :chase-speed 0.1
                        :cooldown 2000
                        :damage-buffer-size 100
                        :damage-fn #(utils/rand-between 150 200)
                        :drop {:items [:hp-potion :mp-potion]
                               :count-fn #(utils/rand-between 1 3)}
                        :target-locked-threshold 10000
                        :target-pos-gap-threshold 0.2
                        :re-spawn-interval 12000})
   :skeleton-champion (merge
                        (:skeleton-champion common.npc/npcs)
                        {:attack-range-threshold 0.6
                         :change-pos-interval 15000
                         :change-pos-speed 0.02
                         :chase-range-threshold 20
                         :chase-speed 0.12
                         :cooldown 2000
                         :damage-buffer-size 150
                         :damage-fn #(utils/rand-between 300 450)
                         :drop {:items [:hp-potion :mp-potion]
                                :count-fn #(utils/rand-between 3 8)}
                         :target-locked-threshold 10000
                         :target-pos-gap-threshold 0.2
                         :re-spawn-interval 14000})
   :burning-skeleton (merge
                       (:burning-skeleton common.npc/npcs)
                       {:attack-range-threshold 0.6
                        :change-pos-interval 15000
                        :change-pos-speed 0.02
                        :chase-range-threshold 20
                        :chase-speed 0.12
                        :cooldown 2000
                        :damage-buffer-size 150
                        :damage-fn #(utils/rand-between 200 350)
                        :drop {:items [:hp-potion :mp-potion]
                               :count-fn #(utils/rand-between 3 8)}
                        :target-locked-threshold 10000
                        :target-pos-gap-threshold 0.2
                        :re-spawn-interval 14000})
   :squid (merge
            (:squid common.npc/npcs)
            {:attack-range-threshold 1.5
             :change-pos-interval 8000
             :change-pos-speed 0.02
             :chase-range-threshold 20
             :chase-speed 0.12
             :cooldown 2000
             :damage-buffer-size 100
             :damage-fn #(utils/rand-between 50 90)
             :drop {:items [:hp-potion :mp-potion]
                    :count-fn #(utils/rand-between 1 2)}
             :target-locked-threshold 10000
             :target-pos-gap-threshold 0.2
             :re-spawn-interval 10000})
   :ghoul (merge
            (:ghoul common.npc/npcs)
            {:attack-range-threshold 1.5
             :change-pos-interval 8000
             :change-pos-speed 0.02
             :chase-range-threshold 20
             :chase-speed 0.12
             :cooldown 2000
             :damage-buffer-size 100
             :damage-fn #(utils/rand-between 80 130)
             :drop {:items [:hp-potion :mp-potion]
                    :count-fn #(utils/rand-between 1 2)}
             :target-locked-threshold 10000
             :target-pos-gap-threshold 0.2
             :re-spawn-interval 10000})
   :demon (merge
            (:demon common.npc/npcs)
            {:attack-range-threshold 1
             :change-pos-interval 7000
             :change-pos-speed 0.02
             :chase-range-threshold 15
             :chase-speed 0.13
             :cooldown 1500
             :damage-buffer-size 100
             :damage-fn #(utils/rand-between 110 160)
             :drop {:items [:hp-potion :mp-potion]
                    :count-fn #(utils/rand-between 1 2)}
             :target-locked-threshold 10000
             :target-pos-gap-threshold 0.2
             :re-spawn-interval 16000})
   :gravestalker (merge
                   (:gravestalker common.npc/npcs)
                   {:attack-range-threshold 0.5
                    :change-pos-interval 42000
                    :change-pos-speed 0.005
                    :chase-range-threshold 15
                    :chase-speed 0.13
                    :cooldown 2000
                    :damage-buffer-size 100
                    :damage-fn #(utils/rand-between 300 400)
                    :drop {:items [:hp-potion :mp-potion]
                           :count-fn #(utils/rand-between 1 2)}
                    :target-locked-threshold 10000
                    :target-pos-gap-threshold 0.2
                    :re-spawn-interval 22000})
   :deruvish (merge
               (:deruvish common.npc/npcs)
               {:attack-range-threshold 7
                :change-pos-interval 24000
                :change-pos-speed 0.02
                :chase-range-threshold 25
                :chase-speed 0.12
                :attack-when-close-range-threshold 2
                :cooldown 1200
                :damage-buffer-size 150
                :damage-fn #(utils/rand-between 300 400)
                :drop {:items [:hp-potion :mp-potion]
                       :count-fn #(utils/rand-between 1 2)}
                :target-locked-threshold 10000
                :target-pos-gap-threshold 0.2
                :re-spawn-interval 21000})})
