(ns enion-cljs.ui.db
  (:require
    [enion-cljs.common :refer [dev?]]
    [enion-cljs.ui.ring-buffer :as rb]))

(def default-db
  {:name "Enion Online"
   :show-ui-panel? true
   :info-box {:messages (rb/ring-buffer 50)
              :open? true}
   :chat-box {:messages {:all (rb/ring-buffer 50)}
              :open? true
              :active-input? false
              :type :all}
   :fullscreen? false
   :init-modal {:open? true}
   :score-board {:open? false}
   :connection-lost? false
   :congrats-text? false
   :char-panel-open? false
   :shop-panel-open? false
   :player {:skills []
            :skill-move nil
            :defense-break? false}
   :servers {:list (when dev?
                     {"local" {:ws-url "ws://localhost:3000/ws"
                               :stats-url "http://localhost:3000/stats"
                               :name "local"
                               :number-of-players 0
                               :max-number-of-players 40}})}
   :global-message-id 0})
