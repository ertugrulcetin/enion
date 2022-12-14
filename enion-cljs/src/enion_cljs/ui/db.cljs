(ns enion-cljs.ui.db
  (:require
    [enion-cljs.ui.ring-buffer :as rb]))

(def default-db
  {:name "Enion Online"
   :info-box {:messages (rb/ring-buffer 50)
              :open? true}
   :chat-box {:messages {:all (conj (rb/ring-buffer 50) {:from "System" :text "Welcome to Enion Online!"})
                         :party (rb/ring-buffer 50)}
              :open? true
              :active-input? false
              :type :all}})
