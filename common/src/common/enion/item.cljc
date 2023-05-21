(ns common.enion.item)

(def inventory-size 24)


(def items
  {:maul {:name "Maul"
          :type :weapon
          :sub-type :axe
          :class :priest
          :entity "item_maul"
          :levels {1 {:ap 20}
                   2 {:ap 30}
                   3 {:ap 45}
                   4 {:ap 60}
                   5 {:ap 70}
                   6 {:ap 95}
                   7 {:ap 130}
                   8 {:ap 160}
                   9 {:ap 185}}
          :buy 25000
          :sell 5000
          :img "0.png"}
   :holy-square-edge {:name "Holy Square Edge"
                      :type :weapon
                      :sub-type :sword
                      :class :priest
                      :entity "item_holy_square_edge"
                      :levels {1 {:ap 35}
                               2 {:ap 45}
                               3 {:ap 60}
                               4 {:ap 75}
                               5 {:ap 95}
                               6 {:ap 135}
                               7 {:ap 150}
                               8 {:ap 190}
                               9 {:ap 215}}
                      :buy 50000
                      :sell 10000
                      :img "23.png"}
   :censure-axe {:name "Censure Axe"
                 :type :weapon
                 :sub-type :axe
                 :class :priest
                 :entity "item_censure_axe"
                 :levels {1 {:ap 60}
                          2 {:ap 70}
                          3 {:ap 85}
                          4 {:ap 100}
                          5 {:ap 120}
                          6 {:ap 150}
                          7 {:ap 190}
                          8 {:ap 220}
                          9 {:ap 245}}
                 :buy 150000
                 :sell 30000
                 :img "22.png"}
   :whispercut-dagger {:name "Whispercut Dagger"
                       :type :weapon
                       :sub-type :dagger
                       :class :asas
                       :entity "item_whispercut_dagger"
                       :levels {1 {:ap 20}
                                2 {:ap 30}
                                3 {:ap 45}
                                4 {:ap 60}
                                5 {:ap 70}
                                6 {:ap 95}
                                7 {:ap 130}
                                8 {:ap 160}
                                9 {:ap 185}}
                       :buy 25000
                       :sell 5000
                       :img "25.png"}
   :nightmark-dagger {:name "Nightmark Dagger"
                      :type :weapon
                      :sub-type :dagger
                      :class :asas
                      :entity "item_nightmark_dagger"
                      :levels {1 {:ap 35}
                               2 {:ap 45}
                               3 {:ap 60}
                               4 {:ap 75}
                               5 {:ap 95}
                               6 {:ap 135}
                               7 {:ap 150}
                               8 {:ap 190}
                               9 {:ap 215}}
                      :buy 50000
                      :sell 10000
                      :img "26.png"}
   :viperslice-dagger {:name "Viperslice Dagger"
                       :type :weapon
                       :sub-type :dagger
                       :class :asas
                       :entity "item_viperslice_dagger"
                       :levels {1 {:ap 60}
                                2 {:ap 70}
                                3 {:ap 85}
                                4 {:ap 100}
                                5 {:ap 120}
                                6 {:ap 150}
                                7 {:ap 190}
                                8 {:ap 220}
                                9 {:ap 245}}
                       :buy 150000
                       :sell 30000
                       :img "24.png"}
   :bone-oracle-staff {:name "Bone Oracle Staff"
                       :type :weapon
                       :sub-type :staff
                       :class :mage
                       :entity "item_bone_oracle"
                       :levels {1 {:ap 20}
                                2 {:ap 30}
                                3 {:ap 45}
                                4 {:ap 60}
                                5 {:ap 70}
                                6 {:ap 95}
                                7 {:ap 130}
                                8 {:ap 160}
                                9 {:ap 185}}
                       :buy 25000
                       :sell 5000
                       :img "14.png"}
   :twilight-crystal-staff {:name "Twilight Crystal Staff"
                            :type :weapon
                            :sub-type :staff
                            :class :mage
                            :entity "item_twilight_crystal"
                            :levels {1 {:ap 35}
                                     2 {:ap 45}
                                     3 {:ap 60}
                                     4 {:ap 75}
                                     5 {:ap 95}
                                     6 {:ap 135}
                                     7 {:ap 150}
                                     8 {:ap 190}
                                     9 {:ap 215}}
                            :buy 50000
                            :sell 10000
                            :img "13.png"}
   :aetherwind-staff {:name "Aetherwind Staff"
                      :type :weapon
                      :sub-type :staff
                      :class :mage
                      :entity "item_aetherwind_staff"
                      :levels {1 {:ap 60}
                               2 {:ap 70}
                               3 {:ap 85}
                               4 {:ap 100}
                               5 {:ap 120}
                               6 {:ap 150}
                               7 {:ap 190}
                               8 {:ap 220}
                               9 {:ap 245}}
                      :buy 150000
                      :sell 30000
                      :img "15.png"}
   :ironclash-sword {:name "Ironclash Sword"
                     :type :weapon
                     :sub-type :sword
                     :class :warrior
                     :entity "item_ironclash_sword"
                     :levels {1 {:ap 20}
                              2 {:ap 30}
                              3 {:ap 45}
                              4 {:ap 60}
                              5 {:ap 70}
                              6 {:ap 95}
                              7 {:ap 130}
                              8 {:ap 160}
                              9 {:ap 185}}
                     :buy 25000
                     :sell 5000
                     :img "21.png"}
   :steelstrike-sword {:name "Steelstrike Sword"
                       :type :weapon
                       :sub-type :sword
                       :class :warrior
                       :entity "item_steel_strike_sword"
                       :levels {1 {:ap 35}
                                2 {:ap 45}
                                3 {:ap 60}
                                4 {:ap 75}
                                5 {:ap 95}
                                6 {:ap 135}
                                7 {:ap 150}
                                8 {:ap 190}
                                9 {:ap 215}}
                       :buy 50000
                       :sell 10000
                       :img "19.png"}
   :thunderlord-axe {:name "Thunderlord Axe"
                     :type :weapon
                     :sub-type :axe
                     :class :warrior
                     :entity "item_thunderlord_axe"
                     :levels {1 {:ap 60}
                              2 {:ap 70}
                              3 {:ap 85}
                              4 {:ap 100}
                              5 {:ap 120}
                              6 {:ap 150}
                              7 {:ap 190}
                              8 {:ap 220}
                              9 {:ap 245}}
                     :buy 150000
                     :sell 30000
                     :img "20.png"}
   :woodguard-shield {:name "Woodguard Shield"
                      :type :shield
                      :entity "item_woodguard_shield"
                      :levels {1 {:defence 3}
                               2 {:defence 5}
                               3 {:defence 7}
                               4 {:defence 10}
                               5 {:defence 13}
                               6 {:defence 15}
                               7 {:defence 18}
                               8 {:defence 21}
                               9 {:defence 25}}
                      :buy 50000
                      :sell 10000
                      :img "27.png"}
   :ironwall-shield {:name "Ironwall Shield"
                     :type :shield
                     :entity "item_ironwall_shield"
                     :levels {1 {:defence 6}
                              2 {:defence 8}
                              3 {:defence 10}
                              4 {:defence 13}
                              5 {:defence 16}
                              6 {:defence 18}
                              7 {:defence 21}
                              8 {:defence 24}
                              9 {:defence 30}}
                     :buy 100000
                     :sell 20000
                     :img "28.png"}
   :titan-aegis-shield {:name "Titan Aegis Shield"
                        :type :shield
                        :entity "item_titan_shield"
                        :levels {1 {:defence 9}
                                 2 {:defence 12}
                                 3 {:defence 15}
                                 4 {:defence 18}
                                 5 {:defence 21}
                                 6 {:defence 25}
                                 7 {:defence 30}
                                 8 {:defence 33}
                                 9 {:defence 35}}
                        :buy 150000
                        :sell 30000
                        :img "29.png"}
   :journeyman-scroll {:name "Journeyman Scroll"
                       :type :scroll
                       :inc-chance 5
                       :buy 5000
                       :sell 1000
                       :img "16.png"}
   :expert-scroll {:name "Expert Scroll"
                   :type :scroll
                   :inc-chance 10
                   :buy 15000
                   :sell 3000
                   :img "17.png"}
   :grandmaster-scroll {:name "Grandmaster Scroll"
                        :type :scroll
                        :inc-chance 20
                        :buy 40000
                        :sell 8000
                        :img "18.png"}})
