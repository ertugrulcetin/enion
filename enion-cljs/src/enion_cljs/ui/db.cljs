(ns enion-cljs.ui.db
  (:require
    [enion-cljs.ui.ring-buffer :as rb]))

(def default-db
  {:name "Enion Online"
   :info-box {:messages (rb/ring-buffer 50)
              :open? true}
   :chat-box {:messages {:all (conj (rb/ring-buffer 50)
                                    {:from "System" :text "Welcome to Enion Online!"}
                                    {:from "System" :text "Press Z to select nearest enemy"}
                                    {:from "System" :text "Press X to run towards the selected enemy"}
                                    {:from "System" :text "Press R to cancel enemy attack skill when close"}
                                    {:from "System" :text "Click 'Add to party' to add players to your party"})
                         :party (rb/ring-buffer 50)}
              :open? true
              :active-input? false
              :type :all}
   :minimap-open? true
   :init-modal {:open? true}
   :party-list-open? true
   :player {:skills []
            :skill-move nil}})
