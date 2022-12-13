(ns enion-cljs.ui.simulation.chat
  (:require
    [enion-cljs.ui.events :as events]
    [enion-cljs.ui.subs :as subs]
    [re-frame.core :refer [dispatch dispatch-sync subscribe]]))

(dotimes [_ 100]
  (dispatch [::events/add-message-to-chat-all
             {:from "Ertus"
              :text (str (random-uuid) "asss sadsadad sda")}]))

(dispatch [::events/add-message-to-chat-party
           {:from "Ertus"
            :text "Aga kankam geldi partye alir misin"}])

@(subscribe [::subs/chat-messages])
