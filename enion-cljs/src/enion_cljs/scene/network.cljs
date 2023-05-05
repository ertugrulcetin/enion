(ns enion-cljs.scene.network
  (:require
    [applied-science.js-interop :as j]
    [cljs.reader :as reader]
    [clojure.string :as str]
    [common.enion.npc :as common.npc]
    [common.enion.skills :as common.skills]
    [enion-cljs.common :refer [dev? fire on dlog ws-url]]
    [enion-cljs.scene.drop :as drop]
    [enion-cljs.scene.entities.npc :as npc]
    [enion-cljs.scene.pc :as pc]
    [enion-cljs.scene.poki :as poki]
    [enion-cljs.scene.skills.effects :as effects]
    [enion-cljs.scene.states :as st]
    [enion-cljs.scene.utils :as utils]
    [enion-cljs.utils :as common.utils]
    [msgpack-cljs.core :as msg]))

(defonce socket (atom nil))
(defonce server-name (atom nil))
(defonce open? (atom false))
(defonce world nil)
(defonce current-player-id nil)

(defonce send-state-interval-id (atom nil))
(defonce get-ping-interval-id (atom nil))

(def sending-states-to-server-tick-rate (/ 1000 30))

(defn connect [{:keys [url on-open on-message on-close on-error]}]
  (reset! socket (js/WebSocket. url))
  (j/assoc! @socket :binaryType "arraybuffer")
  (some->> on-open (j/call @socket :addEventListener "open"))
  (some->> on-message (j/call @socket :addEventListener "message"))
  (some->> on-close (j/call @socket :addEventListener "close"))
  (some->> on-error (j/call @socket :addEventListener "error")))

(defn dispatch-pro
  ([pro]
   (dispatch-pro pro nil))
  ([pro data]
   (cond
     (not (st/tab-visible?))
     (js/console.warn "Tab not focused, can't send the payload!")

     (and @open? @socket)
     (j/call @socket :send (msg/pack {:pro pro
                                      :data data}))
     :else
     (js/console.warn "Connection closed, can't send the payload!"))))

(defmulti dispatch-pro-response ffirst)

(defn- get-potions []
  (let [default {:hp-potions 50
                 :mp-potions 50}]
    (if-let [ls (common.utils/get-local-storage)]
      (try
        (let [potions (j/call ls :getItem "potions")]
          (if (str/blank? potions)
            default
            (reader/read-string potions)))
        (catch js/Error _
          default))
      default)))

(defn- get-player-token []
  (utils/get-item "token"))

(defn- save-token-if-new-player [data]
  (when (:new-player? data)
    (utils/set-item "token" (:token data))))

(defn- stop-intro-music []
  (some-> (js/document.getElementById "enion-intro") (j/call :pause)))

(defmethod dispatch-pro-response :init [params]
  (if-let [error (-> params :init :error)]
    (do
      (j/call @socket :close 3001 "init-error")
      (fire :ui-init-modal-error
            (case error
              :invalid-username (str "Username must be between 2 and 20 characters long and can only contain "
                                     "letters, numbers, and underscores.")
              :username-taken "Username is already taken."
              :invalid-race "Race is not selected."
              :invalid-class "Class is not selected."
              :server-full "Server is full."
              :orc-race-full "Orc race is full."
              :human-race-full "Human race is full."
              :something-went-wrong "Something went wrong.")))
    (let [potions (get-potions)
          data (merge (:init params) potions)]
      (fire :close-init-modal)
      (fire :init data)
      (set! current-player-id (-> params :init :id))
      (fire :ui-init-game (assoc data :server-name @server-name))
      (fire :ui-init-tutorial-data data)
      (fire :check-available-quests (:quests data))
      (save-token-if-new-player data)
      (stop-intro-music)
      (poki/gameplay-start)
      (fire :init-not-preloaded-entities))))

(defmethod dispatch-pro-response :player-join [params]
  (dlog "new join" (:player-join params))
  (fire :create-players [(:player-join params)]))

(defmethod dispatch-pro-response :player-exit [params]
  (dlog "player left")
  (-> (:player-exit params) st/destroy-player))

(defmulti party-response #(-> % :party :type))

(defmethod dispatch-pro-response :party [params]
  (dlog "party" params)
  (if-let [error (-> params :party :error)]
    (fire :show-text (hash-map error true))
    (party-response params)))

(defmethod party-response :party-request [params]
  (let [player-id (-> params :party :player-id)]
    (fire :ui-show-party-request-modal
          {:username (st/get-username player-id)
           :on-accept #(dispatch-pro :party {:type :accept-party-request
                                             :requested-player-id player-id})
           :on-reject #(dispatch-pro :party {:type :reject-party-request
                                             :requested-player-id player-id})})))

(defn- register-party-members [party-member-ids*]
  (reduce (fn [acc id]
            (let [player (if (= id current-player-id)
                           st/player
                           (st/get-other-player id))]
              (assoc acc id {:id id
                             :username (j/get player :username)
                             :health (j/get player :health)
                             :total-health (j/get player :total-health)
                             :order (.indexOf party-member-ids* id)})))
          {} party-member-ids*))

(defn- update-username-text-color [party-member-ids* in-the-party?]
  (doseq [id party-member-ids*
          :let [template-entity (if (= id current-player-id)
                                  (j/get st/player :template-entity)
                                  (j/get (st/get-other-player id) :template-entity))
                username-text-entity (some-> template-entity (pc/find-by-name "char_name"))]
          :when username-text-entity]
    (j/assoc-in! username-text-entity [:element :color] (if in-the-party?
                                                          st/username-party-color
                                                          st/username-color))))

(defmethod party-response :accepted-party-request [params]
  (let [player-id (-> params :party :player-id)]
    (when (empty? @st/party-member-ids)
      (vswap! st/party-member-ids conj current-player-id))
    (vswap! st/party-member-ids conj player-id)
    (fire :ui-set-as-party-leader)
    (update-username-text-color @st/party-member-ids true)
    (fire :show-text {:joined-party (st/get-username player-id)})
    (fire :register-party-members (register-party-members @st/party-member-ids))))

(defmethod party-response :joined-party [params]
  (let [player-id (-> params :party :player-id)]
    (vswap! st/party-member-ids conj player-id)
    (update-username-text-color @st/party-member-ids true)
    (fire :show-text {:joined-party (st/get-username player-id)})
    (fire :register-party-members (register-party-members @st/party-member-ids))))

(def party-cancelled-msg {:party-cancelled true})

(defn- cancel-party []
  (update-username-text-color @st/party-member-ids false)
  (vreset! st/party-member-ids [])
  (fire :cancel-party)
  (fire :show-text party-cancelled-msg))

(defmethod party-response :exit-from-party [_]
  (cancel-party))

(defmethod party-response :remove-from-party [params]
  (let [player-id (-> params :party :player-id)
        party-cancelled? (-> params :party :party-cancelled?)
        this-player? (= current-player-id player-id)]
    (if (or this-player? party-cancelled?)
      (cancel-party)
      (do
        (vswap! st/party-member-ids (fn [ids] (vec (remove #(= player-id %) ids))))
        (update-username-text-color [player-id] false)
        (fire :register-party-members (register-party-members @st/party-member-ids))
        (fire :show-text {:member-removed-from-party (st/get-username player-id)})))))

(defmethod party-response :party-cancelled [_]
  (cancel-party))

(defmethod party-response :member-exit-from-party [params]
  (let [player-id (-> params :party :player-id)
        username (-> params :party :username)]
    (if (= 2 (count @st/party-member-ids))
      (cancel-party)
      (do
        (vswap! st/party-member-ids (fn [ids] (vec (remove #(= player-id %) ids))))
        (fire :register-party-members (register-party-members @st/party-member-ids))
        (update-username-text-color [player-id] false)
        (fire :show-text {:member-exit-from-party username})))))

(let [no-selected-player-msg {:no-selected-player true}]
  (on :add-to-party
      (fn []
        (if-let [selected-player-id (st/get-selected-player-id)]
          (dispatch-pro :party {:type :add-to-party
                                :selected-player-id (js/parseInt selected-player-id)})
          (fire :show-text no-selected-player-msg)))))

(on :exit-from-party
    (fn []
      (dispatch-pro :party {:type :exit-from-party})))

(on :remove-from-party
    (fn [selected-player-id]
      (dispatch-pro :party {:type :remove-from-party
                            :selected-player-id (js/parseInt selected-player-id)})))

(on :finish-tutorial
    (fn [tutorial-step]
      (dispatch-pro :finish-tutorial {:tutorial tutorial-step})))

(on :reset-tutorials
    (fn []
      (dispatch-pro :finish-tutorial {:tutorial :reset})
      (j/assoc! st/player :tutorials #{})
      (j/assoc! st/camera-state :right-mouse-dragged? false)
      (fire :ui-show-tutorial-message)))

(defmethod party-response :add-to-party [params]
  (let [username (-> params :party :selected-player-id st/get-username)]
    (fire :show-text {:party-requested-user username})))

(defmethod party-response :accept-party-request [params]
  (let [party-member-ids* (-> params :party :party-members-ids vec)]
    (vreset! st/party-member-ids party-member-ids*)
    (update-username-text-color party-member-ids* true)
    (fire :register-party-members (register-party-members party-member-ids*))
    (fire :close-party-request-modal)))

(defmethod party-response :reject-party-request [params]
  (fire :close-party-request-modal))

(defmethod party-response :party-request-rejected [params]
  (fire :show-text {:party-request-rejected (-> params :party :player-id st/get-username)}))

(comment
  (dispatch-pro :party {:type :exit-from-party
                        :selected-player-id 10})
  )

(defn- process-world-snapshot-for-player [state]
  (let [health (:health state)
        mana (:mana state)
        current-health (st/get-health)]
    (when (not (and (= health 0) (= 0 current-health)))
      (do
        (st/set-health health)
        (st/set-mana mana)))))

(on :rewarded-break-re-spawn
    (fn []
      (dispatch-pro :re-spawn {:commercial-break-rewarded true})))

(let [skills-effects-before-response #{"heal" "cure" "breakDefense" "attackRange" "attackSingle" "attackIce"}
      temp-pos (pc/vec3)]
  (defn- process-world-snapshot [world]
    (doseq [s world
            :let [id (str (first s))
                  new-state (second s)
                  other-player (st/get-other-player id)
                  entity (j/get other-player :entity)
                  health (:health new-state)
                  new-anim-state (:st new-state)
                  hide (:hide new-state)
                  new-pos (do
                            (pc/setv temp-pos (:px new-state) (:py new-state) (:pz new-state))
                            temp-pos)
                  prev-pos (j/get-in st/other-players [id :prev-pos])
                  pos (some-> entity pc/get-pos)]
            :when (and entity (or (st/alive? id) (> health 0)))]
      (st/set-health id health)
      (if (= health 0)
        (do
          (pc/set-anim-int (st/get-model-entity id) "health" 0)
          (st/disable-player-collision id))
        (do
          (st/enable-player-collision id)
          (pc/set-anim-int (st/get-model-entity id) "health" health)
          (when-let [tw (j/get-in st/other-players [id :tween :interpolation])]
            (if dev? (j/call (tw) :stop) (j/call tw :stop)))

          ;; TODO bu checkten dolayi karakter havada basliyor
          ;; TODO also do not run (st/move-player) for players far away - LOD optimization
          (when (or (not= "idle" new-anim-state)
                    (not (some-> id st/get-model-entity pc/get-anim-state (= "idle")))
                    (or (nil? prev-pos)
                        (not (j/call prev-pos :equals pos))))
            (let [x (j/get pos :x)
                  y (j/get pos :y)
                  z (j/get pos :z)
                  initial-pos (j/get-in st/other-players [id :tween :initial-pos])
                  _ (j/assoc! initial-pos :x x :y y :z z)
                  last-pos (j/get-in st/other-players [id :tween :last-pos])
                  _ (j/assoc! last-pos :x (:px new-state) :y (:py new-state) :z (:pz new-state))
                  tween-interpolation (-> (j/call entity :tween initial-pos)
                                          (j/call :to last-pos 0.05 pc/linear))
                  _ (j/call tween-interpolation :on "update"
                            (fn []
                              (let [x (j/get initial-pos :x)
                                    y (j/get initial-pos :y)
                                    z (j/get initial-pos :z)]
                                (st/move-player other-player entity x y z))))]
              (j/call tween-interpolation :start)
              (j/assoc-in! st/other-players [id :tween :interpolation] (if dev?
                                                                         (constantly tween-interpolation)
                                                                         tween-interpolation))))
          (st/rotate-player id (:ex new-state) (:ey new-state) (:ez new-state))
          (if (and prev-pos (j/call prev-pos :equals pos) (= "run" new-anim-state))
            (st/set-anim-state id "idle")
            (st/set-anim-state id new-anim-state))

          (when (and (skills-effects-before-response new-anim-state)
                     (not= (j/get-in st/other-players [id :prev-state]) new-anim-state))
            (case new-anim-state
              "heal" (effects/apply-effect-heal-particles other-player)
              "cure" (effects/apply-effect-cure-particles other-player)
              "breakDefense" (effects/apply-effect-defense-break-particles other-player)
              "attackRange" (effects/apply-effect-flame-particles other-player)
              "attackSingle" (effects/apply-effect-fire-hands other-player)
              "attackIce" (effects/apply-effect-ice-hands other-player)
              nil))
          (when (and (not (nil? hide)) (not= hide (j/get-in st/other-players [id :prev-hide])))
            (if hide
              (j/call-in other-player [:skills :hide])
              (j/call-in other-player [:skills :appear])))))
      (-> st/other-players
          (j/assoc-in! [id :prev-pos] new-pos)
          (j/assoc-in! [id :prev-state] new-anim-state)
          (j/assoc-in! [id :prev-hide] hide)))))

(defn- process-effects
  ([effects]
   (process-effects effects false))
  ([effects npc?]
   (doseq [[e ids] effects
           id ids
           :when (not= id current-player-id)
           :let [player-or-npc-state (if npc?
                                       (st/get-npc id)
                                       (st/get-other-player id))]]
     (case e
       :attack-r (effects/apply-effect-attack-r player-or-npc-state)
       :attack-dagger (effects/apply-effect-attack-dagger player-or-npc-state)
       :attack-stab (effects/apply-effect-attack-stab player-or-npc-state)
       :attack-one-hand (effects/apply-effect-attack-one-hand player-or-npc-state)
       :attack-slow-down (effects/apply-effect-attack-slow-down player-or-npc-state)
       :attack-single (effects/apply-effect-attack-flame player-or-npc-state)
       :attack-ice (effects/apply-effect-ice-spell player-or-npc-state)
       :attack-priest (effects/apply-effect-attack-priest player-or-npc-state)
       :attack-base (effects/apply-effect-attack-cauldron player-or-npc-state)
       :teleport (effects/apply-effect-teleport player-or-npc-state)
       :hp-potion (effects/apply-effect-hp-potion player-or-npc-state)
       :mp-potion (effects/apply-effect-mp-potion player-or-npc-state)
       :fleet-foot (effects/apply-effect-fleet-foot player-or-npc-state)
       :heal (effects/add-player-id-to-healed-ids id)
       :cure (effects/apply-effect-got-cure player-or-npc-state)
       :break-defense (effects/apply-effect-got-defense-break player-or-npc-state)
       :die (effects/apply-effect-die player-or-npc-state)
       :level-up (effects/apply-effect-level-up player-or-npc-state)
       :else (js/console.error "Unknown effect: " e)))))

(let [temp-pos (pc/vec3)]
  (defn- process-attack-ranges [attack-ranges]
    (doseq [data attack-ranges
            :let [id (:id data)
                  x (:x data)
                  y (:y data)
                  z (:z data)]
            :when (not= id current-player-id)]
      (pc/setv temp-pos x y z)
      (j/call-in st/other-players [id :skills :throw-nova] temp-pos))))

(defn- process-kills [kills]
  (doseq [kill kills]
    (let [killer-id (:killer-id kill)
          killer-username (if (= killer-id current-player-id)
                            (j/get st/player :username)
                            (st/get-username killer-id))
          killer-race (if (= killer-id current-player-id)
                        (j/get st/player :race)
                        (j/get (st/get-other-player killer-id) :race))
          killed-id (:killed-id kill)
          killed-username (if (= killed-id current-player-id)
                            (j/get st/player :username)
                            (st/get-username killed-id))]
      (fire :add-global-message {:killer killer-username
                                 :killer-race killer-race
                                 :killed killed-username}))))

(defmethod dispatch-pro-response :world-snapshot [params]
  (when (utils/tab-visible?)
    (let [ws (:world-snapshot params)
          effects (:effects ws)
          npc-effects (:npc-effects ws)
          kills (:kills ws)
          members-with-break-defense (:break-defense ws)
          npcs (:npcs ws)
          ws (dissoc ws :effects :npc-effects :kills :break-defense :npcs)
          attack-ranges (get effects :attack-range)
          effects (dissoc effects :attack-range)]
      (set! world ws)
      (process-world-snapshot-for-player (get ws current-player-id))
      (process-world-snapshot (dissoc ws current-player-id))
      (process-effects effects)
      (process-effects npc-effects true)
      (process-attack-ranges attack-ranges)
      (process-kills kills)
      (npc/process-npcs current-player-id npcs)
      (when-let [ids (seq @st/party-member-ids)]
        (fire :update-party-members {:healths (reduce (fn [acc id]
                                                        (assoc acc id (get-in ws [id :health]))) {} ids)
                                     :members-with-break-defense members-with-break-defense})))))

(defn send-states-to-server []
  (if-let [id @send-state-interval-id]
    (js/clearInterval id)
    (reset! send-state-interval-id (js/setInterval
                                     #(some->> (st/get-state) (dispatch-pro :set-state))
                                     sending-states-to-server-tick-rate))))

(defn cancel-sending-states-to-server []
  (some-> @send-state-interval-id js/clearInterval)
  (reset! send-state-interval-id nil))

(defn- create-ping-interval []
  (if-let [id @get-ping-interval-id]
    (js/clearInterval id)
    (reset! get-ping-interval-id (js/setInterval
                                   #(when (utils/tab-visible?)
                                      (dispatch-pro :ping {:timestamp (js/Date.now)}))
                                   2000))))

(defn cancel-ping-interval []
  (some-> @get-ping-interval-id js/clearInterval)
  (reset! get-ping-interval-id nil))

(defmethod dispatch-pro-response :ping [params]
  (let [ping (:ping params)
        timestamp (:timestamp ping)
        rtt (int (/ (- (js/Date.now) timestamp) 2))
        ping (assoc ping :ping rtt)]
    (fire :ui-update-ping ping)
    (dispatch-pro :set-ping {:ping rtt})))

(defn- check-if-player-not-initialized-correctly []
  (js/setTimeout
    (fn []
      (when (< (j/get (st/get-pos) :y) 0)
        (fire :ui-show-something-went-wrong?)))
    3000))

(defn- create-tutorial-key-press-listener [tutorial codes]
  (let [tutorial-done? (tutorial (j/get st/player :tutorials))
        key-down-fn (when-not tutorial-done?
                      (fn [e]
                        (when (and (not (tutorial (j/get st/player :tutorials)))
                                   (codes (j/get-in e [:event :code]))
                                   (= tutorial (j/get st/player :current-tutorial)))
                          (utils/finish-tutorial-step tutorial))))]
    (when-not tutorial-done?
      (pc/on-keyboard :EVENT_KEYDOWN key-down-fn))))

(on :set-current-tutorial
    (fn [tutorial]
      (j/assoc! st/player :current-tutorial tutorial)))

(defn- register-tutorial-key-events []
  (create-tutorial-key-press-listener :navigate-wasd #{"KeyW" "KeyA" "KeyS" "KeyD"})
  (create-tutorial-key-press-listener :select-enemy #{"Tab"})
  (create-tutorial-key-press-listener :run-to-enemy #{"KeyR"})
  (create-tutorial-key-press-listener :jump #{"Space"})
  (create-tutorial-key-press-listener :open-character-panel #{"KeyC"})
  (create-tutorial-key-press-listener :open-leader-board #{"KeyL"}))

(defmethod dispatch-pro-response :connect-to-world-state [params]
  (when (:connect-to-world-state params)
    (send-states-to-server)
    (dispatch-pro :request-all-players)
    (fire :ui-player-ready)
    (create-ping-interval)
    (check-if-player-not-initialized-correctly)
    (register-tutorial-key-events)))

(defmethod dispatch-pro-response :request-all-players [params]
  (let [params (:request-all-players params)
        players (:players params)
        npcs (:npcs params)
        current-players-ids (set (cons current-player-id (seq (js/Object.keys st/other-players))))
        players (remove #(or (current-players-ids (:id %)) (nil? (:id %))) players)]
    (when (seq players)
      (fire :create-players players))
    (when (seq npcs)
      (npc/init-npcs npcs))))

(defn- on-ws-open [params]
  (reset! server-name (:server-name params))
  (dispatch-pro :init (select-keys params [:username :race :class]))
  (fire :ui-ws-connected))

(defn- on-ws-close []
  (fire :ui-init-modal-error "Connection closed! Please refresh the page.")
  (fire :ui-set-connection-lost))

(def states-to-not-dc #{"init-error" "re-init"})

(on :connect-to-server
    (fn [{:keys [ws-url] :as params}]
      (connect {:url (or (some->> (get-player-token) (str "?token=") (str ws-url)) ws-url)
                :on-message (fn [event]
                              (dispatch-pro-response (msg/unpack (j/get event :data))))
                :on-open (fn []
                           (println "WS connection established.")
                           (j/assoc! st/settings :ws-connected? true)
                           (reset! open? true)
                           (on-ws-open params))
                :on-close (fn [e]
                            (when (= (j/get e :reason) "re-init")
                              (fire :socket-closed-for-re-init))
                            (when-not (states-to-not-dc (j/get e :reason))
                              (j/assoc! st/settings
                                        :ws-connected? false
                                        :ws-closed? false)
                              (on-ws-close))
                            (println "WS connection closed.")
                            (reset! open? false)
                            (reset! socket nil))
                :on-error (fn []
                            (fire :ui-init-modal-error "Couldn't connect to server! Please refresh the page.")
                            (println "WS error occurred!")
                            (reset! open? false))})))

(on :connect-to-world-state #(dispatch-pro :connect-to-world-state))
(on :send-global-message #(dispatch-pro :send-global-message {:msg %}))
(on :send-party-message #(dispatch-pro :send-party-message {:msg %}))
(on :get-score-board #(dispatch-pro :get-score-board))

(defmethod dispatch-pro-response :get-score-board [params]
  (let [players (:get-score-board params)
        players (map
                  (fn [p]
                    (assoc p :username (if (= current-player-id (:id p))
                                         (st/get-username)
                                         (st/get-username (:id p)))))
                  players)]
    (fire :ui-set-score-board players)))

(let [global-msg-error-msg {:type :all
                            :msg "Too many messages sent. Try again after 1 sec."}]
  (defmethod dispatch-pro-response :send-global-message [params]
    (when (-> params :send-global-message :error)
      (fire :ui-chat-error global-msg-error-msg))))

(let [party-msg-error-msg {:type :party
                           :msg "Too many messages sent. Try again after 1 sec."}]
  (defmethod dispatch-pro-response :send-party-message [params]
    (when (-> params :send-party-message :error)
      (fire :ui-chat-error party-msg-error-msg))))

(defmethod dispatch-pro-response :global-message [params]
  (let [msg (:global-message params)
        current-player? (= (:id msg) current-player-id)]
    (fire :add-global-message (assoc msg :username (if current-player?
                                                     (j/get st/player :username)
                                                     (st/get-username (:id msg)))))))

(defmethod dispatch-pro-response :party-message [params]
  (let [msg (:party-message params)
        current-player? (= (:id msg) current-player-id)]
    (fire :add-party-message (assoc msg :username (if current-player?
                                                    (j/get st/player :username)
                                                    (st/get-username (:id msg)))))))

(defmethod dispatch-pro-response :earned-bp [params]
  (let [bp (-> params :earned-bp :bp)
        total-bp (-> params :earned-bp :total)]
    (fire :show-text {:bp bp})
    (fire :ui-set-bp total-bp)))

(defn- process-drop [params]
  (let [drops (-> params :drop :drop)]
    (doseq [[type amount] drops]
      (let [particle-entity (j/get-in st/player [:drop type])
            _ (j/assoc! particle-entity :enabled true)
            par (j/get-in particle-entity [:c :particlesystem])
            _ (j/call par :reset)
            _ (j/call par :play)]
        (case type
          :hp-potion (drop/inc-potion :hp amount)
          :mp-potion (drop/inc-potion :mp amount)
          nil)
        (when (= type :coin)
          (fire :ui-set-total-coin (-> params :drop :total-coin))
          (fire :show-text {:drop {:name (get-in common.npc/drops [type :name])
                                   :amount amount}}))))))

(defn- show-unlocked-skill [level]
  (let [class (st/get-class)
        [skill name] (some
                       (fn [[k v]]
                         (when (and (= class (:class v)) (= level (:required-level v)))
                           [k (:name v)]))
                       common.skills/skills)]
    (when skill
      (fire :show-unlocked-skill skill name))))

(defn- level-up [{:keys [level] :as opts}]
  (effects/apply-effect-level-up st/player)
  (st/level-up opts)
  (fire :ui-level-up opts)
  (st/play-sound "levelUp")
  (show-unlocked-skill level)
  (case level
    3 (fire :show-unlocked-skill-fleet-foot)
    9 (fire :how-to-change-skill-order)
    10 (js/setTimeout #(let [race (st/get-race)
                             against (if (= "orc" race)
                                       "Kill some Humans!"
                                       "Kill some Orcs!")]
                         (fire :ui-show-global-message (str "PvP Unlocked! ⚔️ " against) 15000)) 2000)
    (show-unlocked-skill level)))

(defn- process-exp [params]
  (let [{:keys [exp npc-exp level-up?] :as opts} (:drop params)]
    (if level-up?
      (level-up opts)
      (fire :ui-set-exp exp))
    (fire :show-text {:npc-exp npc-exp})))

(defn- process-quest [params]
  (when-let [quest-npc (j/get st/player :current-quest)]
    (let [npc (-> params :drop :npc)
          required-kills (j/get st/player :required-kills)]
      (when (= npc quest-npc)
        (j/update! st/player :completed-kills (fnil + 0) 1)
        (if (>= (j/get st/player :completed-kills) required-kills)
          (do
            (fire :ui-complete-quest [quest-npc
                                      (-> common.enion.npc/npcs npc :name)
                                      (j/get st/player :completed-kills)
                                      required-kills])
            (st/play-sound "questComplete"))
          (fire :ui-update-quest-progress [(-> common.enion.npc/npcs npc :name)
                                           (j/get st/player :completed-kills)
                                           required-kills]))))))

(on :finish-quest
    (fn [quests]
      (let [current-quest (j/get st/player :current-quest)]
        (dispatch-pro :finish-quest {:quest current-quest
                                     :coin (-> quests current-quest :coin)})
        (j/assoc! st/player
                  :current-quest nil
                  :completed-kills 0
                  :required-kills 0))))

(defmethod dispatch-pro-response :finish-quest [params]
  (let [{:keys [level quest coin total-coin error] :as opts} (:finish-quest params)]
    (if error
      (fire :ui-show-global-message "This quest is already completed" 5000)
      (do
        (j/update! st/player :quests conj quest)
        (when level
          (level-up opts))
        (fire :show-text {:drop {:name "Coin"
                                 :amount coin}})
        (fire :ui-set-total-coin total-coin)
        (fire :check-available-quests (j/get st/player :quests))
        (fire :disable-walls)))))

(defmethod dispatch-pro-response :drop [params]
  (process-drop params)
  (process-exp params)
  (process-quest params))

(let [re-spawn-error-msg {:re-spawn-error true}]
  (defmethod dispatch-pro-response :re-spawn [params]
    (if (-> params :re-spawn :error)
      (fire :show-text re-spawn-error-msg)
      (let [pos (-> params :re-spawn :pos)
            health (-> params :re-spawn :health)
            mana (-> params :re-spawn :mana)]
        (pc/set-anim-int (st/get-model-entity) "health" 100)
        (when (st/asas?)
          (j/call-in st/player [:skills :appear]))
        (st/move-player pos)
        (j/assoc! st/player :slow-down? false :speed st/speed)
        (fire :ui-re-spawn)
        (st/set-mana mana)
        (st/set-health health)
        (st/cancel-target-pos)
        (poki/gameplay-start)))))

(defmethod dispatch-pro-response :died [_]
  (st/set-health 0)
  (st/set-mana 0)
  (pc/set-anim-int (st/get-model-entity) "health" 0)
  (effects/apply-effect-die st/player)
  (st/play-sound "die")
  (js/setTimeout
    (fn []
      (fire :show-re-spawn-modal #(dispatch-pro :re-spawn)))
    2000)
  (poki/gameplay-stop))

(on :close-socket-for-re-init
    (fn []
      (set! effects/healed-player-ids (js/Set.))
      (vreset! st/party-member-ids [])
      (poki/gameplay-stop)
      (poki/commercial-break)
      (some-> @socket (j/call :close 3001 "re-init"))
      (cancel-sending-states-to-server)
      (cancel-ping-interval)))
