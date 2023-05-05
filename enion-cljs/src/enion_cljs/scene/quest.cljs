(ns enion-cljs.scene.quest
  (:require
    [clojure.set :as set]
    [enion-cljs.common :refer [on fire]]
    [enion-cljs.scene.pc :as pc]
    [enion-cljs.scene.states :as st]
    [enion-cljs.ui.intro :as intro]))

(def squid
  [{:name :squid
    :title "Squid Quest"
    :intro (str "Kill <b>1 Squid</b> to complete quest and earn <b>500</b> Coins!<br/>"
                "<img src=\"img/npc/squid.png\" style=\"position: relative;left: calc(50% - 64px);top: 20px; \">")}])

(def ghoul
  [{:name :ghoul
    :title "Ghoul Quest"
    :intro (str "Kill <b>2 Ghouls</b> to complete quest and earn <b>1000</b> Coins!<br/>"
                "<img src=\"img/npc/ghoul.png\" style=\"position: relative;left: calc(50% - 64px);top: 20px; \">")}])

(def demon
  [{:name :demon
    :title "Demon Quest"
    :intro (str "Kill <b>3 Demons</b> to complete quest and earn <b>2000</b> Coins!<br/>"
                "<img src=\"img/npc/demon.png\" style=\"position: relative;left: calc(50% - 64px);top: 20px; \">")}])

(def complete-quest
  [{:title "Quest completed!"
    :intro "You have successfully completed the quest✨ <br/><br/> Be ready for the next one!"}])

(def no-quests-available
  [{:title "No quests available"
    :intro "There are no quests available at the moment. <br/><br/> Come back later!"}])

(def all-quests
  {:quests {:squid {:steps squid
                    :required-kills 1
                    :coin 500}
            :ghoul {:steps ghoul
                    :required-kills 2
                    :coin 1000}
            :demon {:steps demon
                    :required-kills 3
                    :coin 2000}}
   :order [squid ghoul demon]})

(on :ui-show-talk-to-npc
    (fn [show?]
      (if show?
        (fire :ui-show-global-message {:text "Talk to NPC"
                                       :action-key "F"})
        (fire :ui-show-tutorial-message))))

(on :talk-to-quest-npc
    (fn []
      (fire :ui-talk-to-npc (map (comp :name first) (:order all-quests)))))

(on :show-quest-modal
    (fn [quest]
      (intro/start-intro
        (-> :quests all-quests quest :steps)
        #(fire :player-in-quest [quest (-> :quests all-quests quest :required-kills)])
        false)))

(on :show-complete-quest-modal
    (fn []
      (intro/start-intro
        complete-quest
        #(fire :finish-quest (:quests all-quests))
        false)))

(on :show-no-quests-modal
    (fn []
      (intro/start-intro no-quests-available nil false)))

(on :check-available-quests
    (fn [completed-quests]
      (if-not (pc/enabled? (pc/find-by-name "towns"))
        (js/setTimeout #(fire :check-available-quests completed-quests) 1000)
        (let [remained-quests (set/difference (-> all-quests :quests keys set) completed-quests)
              race (st/get-race)
              npc (pc/find-by-name (if (= "orc" race) "orc_npc" "human_npc"))
              quest-indicator (pc/find-by-name npc "quest_indicator")]
          (if (empty? remained-quests)
            (pc/disable quest-indicator)
            (pc/enable quest-indicator))))))

(comment
  (pc/enable (pc/find-by-name (pc/find-by-name "orc_npc") "quest_indicator"))
  (js/console.log (pc/find-all-by-name "quest_indicator"))
  (intro/start-intro demon)
  st/player
  )
