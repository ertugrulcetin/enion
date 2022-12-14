(ns enion-cljs.ui.simulation.chat
  (:require
    [enion-cljs.ui.events :as events]
    [enion-cljs.ui.subs :as subs]
    [re-frame.core :refer [dispatch dispatch-sync subscribe]]))

(def info-data
  [{:damage 241 :from "NeaTBuSTeR"}
   {:bp 62}
   {:hit 88 :to "LeXXo"}
   {:skill "Smash raptor"}
   {:skill-failed true}
   {:too-far true}
   {:potion :hp}
   {:potion :mp}
   {:hp true}
   {:mp true}])

(defn wrap-hm [data]
  (into {} (map-indexed vector data)))

(def people ["Deidra" "Xuan" "Selim" "ahmet002" "LeXXo" "NeaTBUsTeR" "Valvalvalval200"])
(def messages (repeatedly 20 #(str (random-uuid))))

(def chat-data
  (wrap-hm (repeatedly 100 (fn [] {:from (first (shuffle people))
                                   :text (first (shuffle messages))}))))

(def info-box-data (wrap-hm (repeatedly 100 (fn [] (first (shuffle info-data))))))

(def js-arr (make-array 1))
(def tt (transient []))

(comment
  (conj! tt 1)
  tt
  (.push js-arr 2)
  (.shift js-arr)
  js-arr
  (count js-arr)

  (js/clearInterval 170)

  (js/setInterval
    (fn []
      (dispatch [::events/add-message-to-info-box-throttled (info-box-data (rand-int (inc 100)))]))
    10)

  (js/setInterval
    (fn []
      (dispatch [::events/add-message-to-chat-all {:from (first (shuffle people))
                                                   :text (first (shuffle messages))}]))
    50)

  (js/setInterval
    (fn []
      (dispatch [::events/add-message-to-chat-party {:from (first (shuffle people))
                                                     :text (first (shuffle messages))}]))
    50)

  (dotimes [_ 100]
    (dispatch [::events/add-message-to-chat-all
               {:from "Ertus"
                :text (str (random-uuid) "asss sadsadad sda")}]))

  (dispatch [::events/add-message-to-chat-all
             {:from "Ertus"
              :text "hey lolaassas"}])

  (dispatch [::events/add-message-to-chat-party
             {:from "Ertus"
              :text "Aga kankam geldi partye alir misin"}])

  @(subscribe [::subs/chat-messages])
  @(subscribe [::subs/info-box-messages])

  (doseq [m info-box-data]
    (dispatch [::events/add-message-to-info-box m]))

  (dispatch [::events/add-message-to-info-box {:mp true}])
  (dispatch [::events/add-message-to-info-box {:hp true}])

  (dotimes [_ 200]
    (dispatch [::events/add-message-to-info-box {:hp true}]))
  )
