(ns enion-cljs.ui.events
  (:require
    [applied-science.js-interop :as j]
    [cljs.reader :as reader]
    [clojure.string :as str]
    [enion-cljs.common :as common :refer [fire]]
    [enion-cljs.ui.db :as db]
    [re-frame.core :refer [reg-event-db reg-event-fx dispatch reg-fx reg-cofx inject-cofx]]))

(defonce throttle-timeouts (atom {}))

(def default-settings
  {:sound? true
   :fps? true
   :ping? true
   :minimap? true
   :camera-rotation-speed 10
   :edge-scroll-speed 50
   :graphics-quality 0.75})

(reg-event-fx
  ::initialize-db
  [(inject-cofx ::settings)]
  (fn [{:keys [settings]} _]
    {:db (assoc db/default-db :settings settings)}))

(reg-fx
  ::set-to-ls
  (fn [[k v]]
    (when-let [ls (j/get js/window :localStorage)]
      (.setItem ls k v))))

(reg-cofx
  ::settings
  (fn [cofx _]
    (try
      (if-let [ls (j/get js/window :localStorage)]
        (assoc cofx :settings (let [settings (j/call ls :getItem "settings")]
                                (if (str/blank? settings)
                                  default-settings
                                  (reader/read-string settings))))
        (assoc cofx :settings default-settings))
      (catch js/Error _
        (assoc cofx :settings default-settings)))))

(reg-event-fx
  ::update-settings
  (fn [{:keys [db]} [_ k v]]
    (if (nil? k)
      {::fire [:settings-updated (:settings db)]}
      (let [db (assoc-in db [:settings k] v)]
        {:db db
         ::set-to-ls ["settings" (pr-str (:settings db))]
         ::fire [:settings-updated (:settings db)]}))))

(reg-event-db
  ::update-ping
  (fn [db [_ ping]]
    (assoc db :ping ping)))

(reg-event-db
  ::open-settings-modal
  (fn [db _]
    (assoc-in db [:settings-modal :open?] true)))

(reg-event-db
  ::close-settings-modal
  (fn [db _]
    (assoc-in db [:settings-modal :open?] false)))

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
  (fn [db [_ {:keys [username msg killer killer-race killed]}]]
    (if killer
      (update-in db [:chat-box :messages :all] conj {:killer killer
                                                     :killer-race killer-race
                                                     :killed killed})
      (update-in db [:chat-box :messages :all] conj {:from username :text msg}))))

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
    (let [timeout-id (-> db :player :cooldown (get skill) :timeout-id)]
      {:db (-> db
               (assoc-in [:player :cooldown skill :in-progress?] false)
               (assoc-in [:player :cooldown skill :timeout-id] nil))
       ::fire [:cooldown-ready? {:ready? true
                                 :skill skill}]
       :fx [(when timeout-id
              [::clear-timeout timeout-id])]})))

(reg-event-db
  ::set-cooldown-timeout-id
  (fn [db [_ id skill]]
    (assoc-in db [:player :cooldown skill :timeout-id] id)))

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
                                         :time (js/Date.now)
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
  ::close-party-request-modal
  (fn [db]
    (assoc-in db [:party-request-modal :open?] false)))

(reg-event-db
  ::select-party-member
  (fn [db [_ id]]
    (assoc-in db [:party :selected-member] id)))

(reg-event-db
  ::set-current-player
  (fn [db [_ [id username]]]
    (-> db
        (assoc-in [:player :id] id)
        (assoc-in [:player :username] username))))

(reg-event-db
  ::set-as-party-leader
  (fn [db]
    (assoc-in db [:party :leader?] true)))

(reg-event-db
  ::show-re-spawn-modal
  (fn [db [_ on-ok]]
    (assoc db :re-spawn-modal {:open? true
                               :time (js/Date.now)
                               :on-ok on-ok})))

(reg-event-db
  ::close-re-spawn-modal
  (fn [db]
    (assoc-in db [:re-spawn-modal :open?] false)))

(reg-event-fx
  ::clear-all-cooldowns
  (fn [{:keys [db]}]
    (let [cooldowns (-> db :player :cooldown keys)]
      {:dispatch-n (mapv (fn [skill]
                           [::clear-cooldown skill]) cooldowns)})))

(reg-event-fx
  ::init-game
  (fn [{:keys [db]} [_ username race class]]
    {:db (-> db
             (assoc-in [:init-modal :error] nil)
             (assoc-in [:init-modal :loading?] true))
     :fx [[::fire [:init-game {:username username
                               :race race
                               :class class}]]]}))

(reg-event-db
  ::close-init-modal
  (fn [db]
    (-> db
        (assoc-in [:init-modal :loading?] false)
        (assoc-in [:init-modal :open?] false))))

(reg-event-db
  ::set-init-modal-error
  (fn [db [_ error]]
    (-> db
        (assoc-in [:init-modal :loading?] false)
        (assoc-in [:init-modal :error] error))))

(reg-event-fx
  ::get-server-stats
  (fn [_]
    {::fire [:get-server-stats]}))

(reg-event-fx
  ::set-server-stats
  (fn [{:keys [db]} [_ stats]]
    (cond-> {:db (assoc db :server-stats stats)}
      (:request-server-stats? db) (assoc :dispatch-later [{:ms 2000
                                                           :dispatch [::get-server-stats]}]))))

(reg-event-db
  ::cancel-request-server-stats
  (fn [db]
    (assoc db :request-server-stats? false)))

(reg-event-fx
  ::toggle-score-board
  (fn [{:keys [db]}]
    (let [open? (-> db :score-board :open? not)]
      {:db (assoc-in db [:score-board :open?] open?)
       :fx [(when open?
              [::fire [:get-score-board]])]})))

(reg-event-db
  ::set-score-board
  (fn [db [_ players]]
    (assoc-in db [:score-board :players] players)))

(reg-event-db
  ::set-connection-lost
  (fn [db]
    (assoc db :connection-lost? true)))

(reg-event-fx
  ::notify-ui-is-ready
  (fn []
    {::fire [:notify-ui-is-ready]}))
