(ns common.enion.npc)

(def lod-2-threshold 13)


(def npcs
  {:squid {:health 1000
           :name "Squid"
           :char-name-y-offset 0.2
           :level 1}
   :ghoul {:health 1750
           :name "Ghoul"
           :char-name-y-offset 0.1
           :level 3}
   :demon {:health 2750
           :name "Demon"
           :char-name-y-offset 0.1
           :level 5}
   :skeleton-warrior {:health 3500
                      :name "Skeleton Warrior"
                      :char-name-y-offset 0.3
                      :level 7}
   :burning-skeleton {:health 4250
                      :name "Burning Skeleton"
                      :char-name-y-offset 0.5
                      :level 9}
   :gravestalker {:health 4500
                  :name "Grave Stalker"
                  :char-name-y-offset 0.3
                  :level 12}
   :skeleton-champion {:health 7500
                       :name "Skeleton Champion"
                       :char-name-y-offset 0.75
                       :level 13}
   :deruvish {:health 10000
              :name "Deruvish Aethertide"
              :char-name-y-offset 0.5
              :level 20}})
