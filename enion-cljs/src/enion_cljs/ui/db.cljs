(ns enion-cljs.ui.db
  (:require
    [enion-cljs.ui.ring-buffer :as rb]))

(def default-db
  {:name "Enion Online"
   :info-box {:messages (rb/ring-buffer 50)
              :open? true}
   :chat-box {:messages {:all (conj (rb/ring-buffer 50)
                                    {:from "System" :text "Press Z to select nearest enemy"}
                                    {:from "System" :text "Press X to run towards the selected enemy"}
                                    {:from "System" :text "Press R to cancel enemy attack skill when close"}
                                    {:from "System" :text "Press Tab to check score board"}
                                    {:from "System" :text "Click 'Add to party' to add players to your party"})
                         :party (rb/ring-buffer 50)}
              :open? true
              :active-input? false
              :type :all}
   :minimap-open? true
   :init-modal {:open? true}
   :party-list-open? true
   :request-server-stats? true
   :score-board {:open? false}
   :connection-lost? false
   :player {:skills []
            :skill-move nil}})
