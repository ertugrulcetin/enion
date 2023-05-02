(ns enion-cljs.ui.quest
  (:require
    [enion-cljs.ui.intro :as intro]))

(def squid
  [{:title "Squid Quest"
    :intro (str "Kill <b>1 Squid</b> to complete quest and earn <b>500</b> Coins!<br/>"
                "<img src=\"img/npc/squid.png\" style=\"position: relative;left: calc(50% - 64px);top: 20px; \">")}])

(def quests
  [])

(comment
  (intro/start-intro squid)
  )
