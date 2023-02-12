(ns enion-cljs.ui.events
  (:require
    [applied-science.js-interop :as j]
    [clojure.string :as str]
    [enion-cljs.common :as common :refer [fire]]
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

(reg-fx
  ::clear-timeout
  (fn [id]
    (some-> id js/clearTimeout)))

(reg-fx
  ::clear-interval
  (fn [id]
    (some-> id js/clearInterval)))

(reg-fx
  ::fire
  (fn [[event x]]
    (fire event x)))

(reg-event-db
  ::add-message-to-info-box*
  (fn [db [_ msg]]
    (update-in db [:info-box :messages] conj msg)))

(reg-event-fx
  ::add-message-to-info-box
  (fn [_ [_ msg]]
    {::dispatch-throttle [::add-message-to-info-box* [::add-message-to-info-box* msg] 100]}))

(reg-event-db
  ::add-message-to-chat-all
  (fn [db [_ {:keys [username msg]}]]
    (update-in db [:chat-box :messages :all] conj {:from username :text msg})))

(reg-event-db
  ::add-message-to-chat-party
  (fn [db [_ {:keys [username msg]}]]
    (update-in db [:chat-box :messages :party] conj {:from username :text msg})))

(reg-event-db
  ::add-chat-error-msg
  (fn [db [_ {:keys [type msg]}]]
    (update-in db [:chat-box :messages type] conj {:from "System" :text msg})))

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
  ::close-chat
  (fn [{:keys [db]}]
    {:db (-> db
             (assoc-in [:chat-box :active-input?] false)
             (assoc-in [:chat-box :message] ""))
     :fx [[::fire [:chat-open? false]]]}))

(reg-event-fx
  ::send-message
  (fn [{:keys [db]}]
    (when (-> db :chat-box :open?)
      (let [input-open? (-> db :chat-box :active-input?)
            msg (-> db :chat-box :message)
            chat-type (or (-> db :chat-box :type) :all)]
        (cond
          input-open? {:db (-> db
                               (assoc-in [:chat-box :active-input?] false)
                               (assoc-in [:chat-box :message] ""))
                       :fx [(when-not (str/blank? msg)
                              (if (= chat-type :all)
                                [::fire [:send-global-message msg]]
                                [::fire [:send-party-message msg]]))
                            [::fire [:chat-open? false]]]}
          (not input-open?) {:db (assoc-in db [:chat-box :active-input?] true)
                             :fx [[::fire [:chat-open? true]]]})))))

(reg-event-db
  ::toggle-minimap
  (fn [db]
    (if (-> db :chat-box :active-input?)
      db
      (update db :minimap-open? not))))

(reg-event-db
  ::toggle-party-list
  (fn [db]
    (if (-> db :chat-box :active-input?)
      db
      (update db :party-list-open? not))))

(reg-event-db
  ::init-skills
  (fn [db [_ class]]
    (let [skills (common/skill-slot-order-by-class class)]
      (assoc-in db [:player :skills] (conj (mapv second (sort skills)) :none :none)))))

(reg-event-fx
  ::update-skills-order
  (fn [{:keys [db]} [_ index skill]]
    (if-let [skill-move (-> db :player :skill-move)]
      (let [skill-move-index (skill-move :index)
            new-skill (skill-move :skill)
            skill-slots (-> db :player :skills)
            prev-skill (skill-slots index)
            db (update-in db [:player :skills] assoc index new-skill skill-move-index prev-skill)]
        {:db (assoc-in db [:player :skill-move] nil)
         ::fire [:update-skills-order (->> db
                                           :player
                                           :skills
                                           (map-indexed #(vector (inc %1) %2))
                                           (remove #(= (second %) :none))
                                           (into {}))]})
      (when-not (= skill :none)
        {:db (assoc-in db [:player :skill-move] {:index index :skill skill})}))))

(reg-event-db
  ::show-skill-description
  (fn [db [_ skill]]
    (assoc db :skill-description skill)))

(reg-event-fx
  ::cooldown
  (fn [{:keys [db]} [_ skill]]
    {:db (assoc-in db [:player :cooldown skill :in-progress?] true)
     ::fire [:cooldown-ready? {:ready? false
                               :skill skill}]}))

(reg-event-fx
  ::clear-cooldown
  (fn [{:keys [db]} [_ skill]]
    {:db (assoc-in db [:player :cooldown skill :in-progress?] false)
     ::fire [:cooldown-ready? {:ready? true
                               :skill skill}]}))

(reg-event-fx
  ::set-cooldown-timeout-id
  (fn [{:keys [db]} [_ id skill]]
    {:db (assoc-in db [:player :cooldown skill :timeout-id] id)
     :fx [(when (nil? id)
            [:dispatch [::clear-cooldown skill]])]}))

;; TODO add throttle to it
(reg-event-db
  ::set-selected-player
  (fn [db [_ player]]
    (if player
      (let [health (j/get player :health)
            total-health (j/get player :total-health)
            health (/ (* health 100) total-health)
            player-id (some-> player (j/get :id) js/parseInt)
            party-member-id (and (get-in db [:party :members player-id]) player-id)]
        (-> db
            (assoc-in [:selected-player :username] (j/get player :username))
            (assoc-in [:selected-player :health] health)
            (assoc-in [:selected-player :enemy?] (j/get player :enemy?))
            (assoc-in [:party :selected-member] party-member-id)))
      (-> db
          (assoc :selected-player nil)
          (assoc-in [:party :selected-member] nil)))))

(reg-event-fx
  ::cancel-skill
  (fn [{:keys [db]} [_ skill]]
    (let [timeout-id (-> db :player :cooldown (get skill) :timeout-id)]
      {:dispatch [::clear-cooldown skill]
       :fx [(when timeout-id
              [::clear-timeout timeout-id])]})))

(reg-event-db
  ::set-total-health-and-mana
  (fn [db [_ {:keys [health mana]}]]
    (-> db
        (assoc-in [:player :total-health] health)
        (assoc-in [:player :total-mana] mana))))

(reg-event-db
  ::set-health
  (fn [db [_ health]]
    (assoc-in db [:player :health] health)))

(reg-event-db
  ::set-mana
  (fn [db [_ mana]]
    (assoc-in db [:player :mana] mana)))

(reg-event-db
  ::cancel-skill-move
  (fn [db]
    (assoc-in db [:player :skill-move] nil)))

;; TODO maybe ::block-skill (generic event) in the future
(reg-event-db
  ::block-slow-down-skill
  (fn [db [_ blocked?]]
    (assoc-in db [:player :slow-down-blocked?] blocked?)))

(reg-event-fx
  ::show-party-request-modal
  (fn [{:keys [db]} [_ {:keys [username on-accept on-reject]}]]
    {:db (assoc db :party-request-modal {:open? true
                                         :username username
                                         :on-accept on-accept
                                         :on-reject on-reject})
     ::clear-interval (-> db :party-request-modal :countdown-interval-id)}))

(reg-event-db
  ::register-party-members
  (fn [db [_ members]]
    (assoc-in db [:party :members] members)))

(reg-event-db
  ::update-party-member-healths
  (fn [db [_ healths]]
    (reduce-kv (fn [db id health]
                 (assoc-in db [:party :members id :health] health)) db healths)))

(reg-event-db
  ::cancel-party
  (fn [db]
    (dissoc db :party)))

(reg-event-db
  ::close-part-request-modal
  (fn [db]
    (assoc-in db [:party-request-modal :open?] false)))

(reg-event-db
  ::select-party-member
  (fn [db [_ id]]
    (assoc-in db [:party :selected-member] id)))

(reg-event-db
  ::set-current-player-id
  (fn [db [_ id]]
    (assoc-in db [:player :id] id)))

(reg-event-db
  ::set-as-party-leader
  (fn [db]
    (assoc-in db [:party :leader?] true)))

(reg-event-db
  ::set-party-request-countdown-interval-id
  (fn [db [_ id]]
    (assoc-in db [:party-request-modal :countdown-interval-id] id)))
