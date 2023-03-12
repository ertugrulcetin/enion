(ns enion-cljs.ui.events
  (:require
    [ajax.core :as ajax]
    [applied-science.js-interop :as j]
    [cljs.reader :as reader]
    [clojure.string :as str]
    [day8.re-frame.http-fx :as http-fx]
    [enion-cljs.common :as common :refer [fire]]
    [enion-cljs.ui.db :as db]
    [enion-cljs.ui.utils :as ui.utils]
    [enion-cljs.utils :as common.utils]
    [re-frame.core :refer [reg-event-db reg-event-fx dispatch reg-fx reg-cofx inject-cofx]]))

(defonce throttle-timeouts (atom {}))

(def default-settings
  {:sound? true
   :fps? false
   :ping? false
   :minimap? true
   :camera-rotation-speed 10
   :edge-scroll-speed 100
   :graphics-quality 0.75})

(reg-event-fx
  ::initialize-db
  [(inject-cofx ::settings)]
  (fn [{:keys [settings]} _]
    {:db (assoc db/default-db :settings settings)}))

(reg-fx
  ::set-to-ls
  (fn [[k v]]
    (when-let [ls (common.utils/get-local-storage)]
      (.setItem ls k v))))

(reg-fx
  :http
  (fn [params]
    (http-fx/http-effect (merge
                           {:timeout 30000
                            :format (ajax/json-request-format)
                            :response-format (ajax/json-response-format {:keywords? true})}
                           params))))

(reg-cofx
  ::settings
  (fn [cofx _]
    (try
      (if-let [ls (common.utils/get-local-storage)]
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
  (fn [db [_ data]]
    (let [ping (:ping data)
          online (:online data)]
      (assoc db :ping ping
             :online online))))

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

(reg-fx
  ::blur
  (fn [elem]
    (.blur elem)))

(reg-event-db
  ::add-message-to-info-box*
  (fn [db [_ msg]]
    (update-in db [:info-box :messages] conj msg)))

(reg-event-fx
  ::add-message-to-info-box
  (fn [_ [_ msg]]
    #_{::dispatch-throttle [::add-message-to-info-box* [::add-message-to-info-box* msg] 100]}
    {:dispatch [::add-message-to-info-box* msg]}))

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
                                [::fire [:send-global-message (ui.utils/clean msg)]]
                                [::fire [:send-party-message (ui.utils/clean msg)]]))
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
  ::init-game
  (fn [db [_ {:keys [id username race class hp-potions mp-potions tutorials server-name]}]]
    (-> db
        (assoc-in [:player :id] id)
        (assoc-in [:player :username] username)
        (assoc-in [:player :race] race)
        (assoc-in [:player :class] class)
        (assoc-in [:player :hp-potions] hp-potions)
        (assoc-in [:player :mp-potions] mp-potions)
        (assoc-in [:servers :current-server] server-name)
        (assoc :tutorials tutorials))))

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
        (assoc-in [:servers :connecting] nil)
        (assoc-in [:init-modal :error] error))))

(reg-event-fx
  ::open-score-board
  (fn [{:keys [db]}]
    {:db (assoc-in db [:score-board :open?] true)
     :fx [[::fire [:get-score-board]]]}))

(reg-event-db
  ::close-score-board
  (fn [db]
    (assoc-in db [:score-board :open?] false)))

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

(reg-event-db
  ::update-hp-potions
  (fn [db [_ hp-potions]]
    (assoc-in db [:player :hp-potions] hp-potions)))

(reg-event-db
  ::update-mp-potions
  (fn [db [_ mp-potions]]
    (assoc-in db [:player :mp-potions] mp-potions)))

(reg-event-db
  ::finish-tutorial-progress
  (fn [db [_ tutorial]]
    (assoc-in db [:tutorials tutorial] true)))

(reg-event-db
  ::hide-congrats-text
  (fn [db]
    (assoc db :congrats-text? false)))

(reg-event-fx
  ::show-congrats-text
  (fn [{:keys [db]} _]
    {:db (assoc db :congrats-text? true)
     :dispatch-later [{:ms 6000
                       :dispatch [::hide-congrats-text]}]}))

(reg-event-fx
  ::show-hp-mp-potions-ads
  (fn []
    {::fire [:rewarded-break]}))

(reg-event-fx
  ::reset-tutorials
  (fn [{:keys [db]}]
    (let [db (update db :tutorials #(select-keys % [:what-is-the-first-quest?]))]
      {:db db
       ::fire [:reset-tutorials (:tutorials db)]})))

(reg-event-db
  ::hide-adblock-warning-text
  (fn [db]
    (assoc db :adblock-warning-text? false)))

(reg-event-fx
  ::show-adblock-warning-text
  (fn [{:keys [db]} _]
    {:db (assoc db :adblock-warning-text? true)
     :dispatch-later [{:ms 5000
                       :dispatch [::hide-adblock-warning-text]}]}))

(reg-event-db
  ::set-ws-connected
  (fn [db]
    (assoc db :ws-connected? true)))

(reg-event-db
  ::show-ui-panel?
  (fn [db [_ show-ui-panel?]]
    (assoc db :show-ui-panel? show-ui-panel?)))

(reg-event-db
  ::clear-init-modal-error
  (fn [db]
    (assoc-in db [:init-modal :error] nil)))

(reg-event-fx
  ::fetch-server-stats
  (fn [{:keys [db]} [_ server-name stats-url]]
    (when (-> db :servers :current-server nil?)
      {:http {:method :post
              :uri stats-url
              :params {:timestamp (js/Date.now)}
              :on-success [::fetch-server-stats-on-success server-name stats-url]
              :on-failure [:dispatch-later [{:ms 2000
                                             :dispatch [::fetch-server-stats server-name stats-url]}]]}})))

(reg-event-fx
  ::fetch-server-stats-on-success
  (fn [{:keys [db]} [_ server-name stats-url response]]
    {:db (update-in db [:servers :list server-name] merge response)
     :dispatch-later [{:ms 2000
                       :dispatch [::fetch-server-stats server-name stats-url]}]}))

(reg-event-fx
  ::connect-to-server
  (fn [{:keys [db]} [_ server-name ws-url username race class]]
    (when-not (-> db :servers :connecting?)
      {:db (assoc-in db [:servers :connecting] server-name)
       ::fire [:connect-to-server {:server-name server-name
                                   :ws-url ws-url
                                   :username username
                                   :race race
                                   :class class}]})))
