(ns enion-cljs.ui.subs
  (:require
    [re-frame.core :refer [reg-sub]]))

(reg-sub
  ::box-open?
  (fn [db [_ type]]
    (-> db type :open?)))

(reg-sub
  ::info-box-messages
  (fn [db]
    (-> db :info-box :messages seq)))

(reg-sub
  ::chat-messages
  (fn [db]
    (let [type (-> db :chat-box :type)]
      (-> db :chat-box :messages type seq))))

(reg-sub
  ::chat-input-active?
  (fn [db]
    (-> db :chat-box :active-input?)))

(reg-sub
  ::chat-type
  (fn [db]
    (-> db :chat-box :type)))
