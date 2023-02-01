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
                                    {:from "System" :text "Press R to cancel enemy attack skill when close"})
                         :party (rb/ring-buffer 50)}
              :open? true
              :active-input? false
              :type :all}
   ;; TODO make them true
   :minimap-open? false
   :party-list-open? false
   :player {:skills []
            :skill-move nil}})
