(ns common.enion.npc)

(def lod-2-threshold 13)


(def npcs
  {:squid {:health 750
           :name "Squid"
           :char-name-y-offset 0.2
           :level 1}
   :ghoul {:health 1500
           :name "Ghoul"
           :char-name-y-offset 0.1
           :level 3}
   :demon {:health 2000
           :name "Demon"
           :char-name-y-offset 0.1
           :level 6}
   :skeleton-warrior {:health 2750
                      :name "Skeleton Warrior"
                      :char-name-y-offset 0.3
                      :level 10}
   :burning-skeleton {:health 3750
                      :name "Burning Skeleton"
                      :char-name-y-offset 0.5
                      :level 15}
   :gravestalker {:health 4250
                  :name "Grave Stalker"
                  :char-name-y-offset 0.3
                  :level 17}
   :skeleton-champion {:health 7500
                       :name "Skeleton Champion"
                       :char-name-y-offset 0.75
                       :level 20}
   :deruvish {:health 10000
              :name "Deruvish Aethertide"
              :char-name-y-offset 0.5
              :level 30}})
