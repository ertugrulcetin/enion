(ns enion-cljs.ui.events
  (:require
    [enion-cljs.ui.db :as db]
    [re-frame.core :refer [reg-event-db reg-event-fx]]))

(reg-event-db
  ::initialize-db
  (fn [_ _]
    db/default-db))

(reg-event-db
  ::add-message-to-info-box
  (fn [db [_ msg]]
    (update-in db [:info-box :messages] conj msg)))

(reg-event-db
  ::add-message-to-chat-all
  (fn [db [_ msg]]
    (update-in db [:chat-box :messages :all] conj msg)))

(reg-event-db
  ::add-message-to-chat-party
  (fn [db [_ msg]]
    (update-in db [:chat-box :messages :party] conj msg)))

(reg-event-db
  ::toggle-box
  (fn [db [_ type]]
    (update-in db [type :open?] not)))

(reg-event-db
  ::toggle-chat-input
  (fn [db [_ type]]
    (update-in db [:chat-box :active-input?] not)))

(reg-event-db
  ::set-chat-type
  (fn [db [_ type]]
    (assoc-in db [:chat-box :type] type)))

(reg-event-db
  ::set-chat-message
  (fn [db [_ msg]]
    (assoc-in db [:chat-box :message] msg)))

(reg-event-fx
  ::send-message
  (fn [{:keys [db]}]
    (when (-> db :chat-box :open?)
      (let [input-open? (-> db :chat-box :active-input?)
            msg (-> db :chat-box :message)]
        ;; TODO implement network layer
        (cond
          input-open? {:db (assoc-in db [:chat-box :active-input?] false)}
          (not input-open?) {:db (assoc-in db [:chat-box :active-input?] true)})))))
