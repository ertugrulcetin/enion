(ns enion-cljs.ui.events
  (:require
    [enion-cljs.ui.db :as db]
    [re-frame.core :refer [reg-event-db reg-event-fx dispatch reg-fx]]))

(defonce throttle-timeouts (atom {}))

(reg-event-db
  ::initialize-db
  (fn [_ _]
    db/default-db))

(reg-fx
  ::dispatch-throttle
  (fn [[id event-vec milli-secs]]
    (when-not (@throttle-timeouts id)
      (swap! throttle-timeouts assoc id
             (js/setTimeout
               (fn []
                 (dispatch event-vec)
                 (swap! throttle-timeouts dissoc id))
               milli-secs)))))

(reg-event-fx
  ::add-message-to-info-box-throttled
  (fn [_ [_ msg]]
    {::dispatch-throttle [::add-message-to-info-box [::add-message-to-info-box msg] 100]}))

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

(reg-event-db
  ::toggle-minimap
  (fn [db]
    (if (-> db :chat-box :active-input?)
      db
      (update db :minimap-open? not))))
