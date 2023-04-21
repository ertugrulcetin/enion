(ns common.enion.npc)

(def lod-2-threshold 13)


(def npcs
  {:squid {:health 1000
           :name "Squid"
           :char-name-y-offset 0.2}
   :ghoul {:health 1750
           :name "Ghoul"
           :char-name-y-offset 0.1}
   :demon {:health 2750
           :name "Demon"
           :char-name-y-offset 0.1}
   :skeleton-warrior {:health 3500
                      :name "Skeleton Warrior"
                      :char-name-y-offset 0.3}
   :burning-skeleton {:health 4250
                      :name "Burning Skeleton"
                      :char-name-y-offset 0.5}
   :gravestalker {:health 4500
                  :name "Grave Stalker"
                  :char-name-y-offset 0.3}
   :skeleton-champion {:health 7500
                       :name "Skeleton Champion"
                       :char-name-y-offset 0.75}
   :deruvish {:health 10000
              :name "Deruvish Aethertide"
              :char-name-y-offset 0.5}})
