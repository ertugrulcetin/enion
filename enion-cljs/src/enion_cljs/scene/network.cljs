(ns enion-cljs.scene.network
  (:require
    [applied-science.js-interop :as j]
    [enion-cljs.common :refer [fire on dlog ws-url]]
    [enion-cljs.scene.pc :as pc]
    [enion-cljs.scene.poki :as poki]
    [enion-cljs.scene.skills.effects :as effects]
    [enion-cljs.scene.states :as st]
    [enion-cljs.scene.utils :as utils]
    [msgpack-cljs.core :as msg]))

(defonce socket (atom nil))
(defonce open? (atom false))
(defonce world nil)
(defonce current-player-id nil)

(defonce send-state-interval-id (atom nil))

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
   (if @open?
     (j/call @socket :send (msg/pack {:pro pro
                                      :data data
                                      :timestamp (js/Date.now)}))
     (js/console.error "Connection closed, can't send the payload!"))))

(defmulti dispatch-pro-response ffirst)

(defmethod dispatch-pro-response :init [params]
  (if-let [error (-> params :init :error)]
    (case error
      :invalid-username (fire :ui-init-modal-error
                              (str "Username must be between 2 and 20 characters long and can only contain "
                                   "letters, numbers, and underscores."))
      :username-taken (fire :ui-init-modal-error "Username is already taken.")
      :invalid-race (fire :ui-init-modal-error "Race is not selected.")
      :invalid-class (fire :ui-init-modal-error "Class is not selected.")
      :server-full (fire :ui-init-modal-error "Server is full. Please wait a bit and try again.")
      :orc-race-full (fire :ui-init-modal-error "Orc race is full. Please wait a bit and try again.")
      :human-race-full (fire :ui-init-modal-error "Human race is full. Please wait a bit and try again."))
    (do
      (fire :close-init-modal)
      (fire :init (:init params))
      (set! current-player-id (-> params :init :id))
      (fire :ui-set-current-player [current-player-id (-> params :init :username)])
      (poki/gameplay-start))))

(defmethod dispatch-pro-response :player-join [params]
  (dlog "new join" (:player-join params))
  (fire :create-players [(:player-join params)]))

(defmethod dispatch-pro-response :player-exit [params]
  (dlog "player left")
  (-> (:player-exit params) st/destroy-player))

(defonce party-member-ids (volatile! []))

(defmulti party-response #(-> % :party :type))

(defmethod dispatch-pro-response :party [params]
  (dlog "party" params)
  (if-let [error (-> params :party :error)]
    (fire :ui-send-msg (hash-map error true))
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
    (when (empty? @party-member-ids)
      (vswap! party-member-ids conj current-player-id))
    (vswap! party-member-ids conj player-id)
    (fire :ui-set-as-party-leader)
    (update-username-text-color @party-member-ids true)
    (fire :ui-send-msg {:joined-party (st/get-username player-id)})
    (fire :register-party-members (register-party-members @party-member-ids))))

(defmethod party-response :joined-party [params]
  (let [player-id (-> params :party :player-id)]
    (vswap! party-member-ids conj player-id)
    (update-username-text-color @party-member-ids true)
    (fire :ui-send-msg {:joined-party (st/get-username player-id)})
    (fire :register-party-members (register-party-members @party-member-ids))))

(def party-cancelled-msg {:party-cancelled true})

(defn- cancel-party []
  (update-username-text-color @party-member-ids false)
  (vreset! party-member-ids [])
  (fire :cancel-party)
  (fire :ui-send-msg party-cancelled-msg))

(defmethod party-response :exit-from-party [_]
  (cancel-party))

(defmethod party-response :remove-from-party [params]
  (let [player-id (-> params :party :player-id)
        party-cancelled? (-> params :party :party-cancelled?)
        this-player? (= current-player-id player-id)]
    (if (or this-player? party-cancelled?)
      (cancel-party)
      (do
        (vswap! party-member-ids (fn [ids] (vec (remove #(= player-id %) ids))))
        (update-username-text-color [player-id] false)
        (fire :register-party-members (register-party-members @party-member-ids))
        (fire :ui-send-msg {:member-removed-from-party (st/get-username player-id)})))))

(defmethod party-response :party-cancelled [_]
  (cancel-party))

(defmethod party-response :member-exit-from-party [params]
  (let [player-id (-> params :party :player-id)
        username (-> params :party :username)]
    (if (= 2 (count @party-member-ids))
      (cancel-party)
      (do
        (vswap! party-member-ids (fn [ids] (vec (remove #(= player-id %) ids))))
        (fire :register-party-members (register-party-members @party-member-ids))
        (update-username-text-color [player-id] false)
        (fire :ui-send-msg {:member-exit-from-party username})))))

(let [no-selected-player-msg {:no-selected-player true}]
  (on :add-to-party
      (fn []
        (if-let [selected-player-id (st/get-selected-player-id)]
          (dispatch-pro :party {:type :add-to-party
                                :selected-player-id (js/parseInt selected-player-id)})
          (fire :ui-send-msg no-selected-player-msg)))))

(on :exit-from-party
    (fn []
      (dispatch-pro :party {:type :exit-from-party})))

(on :remove-from-party
    (fn [selected-player-id]
      (dispatch-pro :party {:type :remove-from-party
                            :selected-player-id (js/parseInt selected-player-id)})))

(defmethod party-response :add-to-party [params]
  (let [username (-> params :party :selected-player-id st/get-username)]
    (fire :ui-send-msg {:party-requested-user username})))

(defmethod party-response :accept-party-request [params]
  (let [party-member-ids* (-> params :party :party-members-ids vec)]
    (vreset! party-member-ids party-member-ids*)
    (update-username-text-color party-member-ids* true)
    (fire :register-party-members (register-party-members party-member-ids*))
    (fire :close-party-request-modal)))

(defmethod party-response :reject-party-request [params]
  (fire :close-party-request-modal))

(defmethod party-response :party-request-rejected [params]
  (fire :ui-send-msg {:party-request-rejected (-> params :party :player-id st/get-username)}))

(comment
  (dispatch-pro :party {:type :exit-from-party
                        :selected-player-id 10})
  )

(defn- process-world-snapshot-for-player [state]
  (let [health (:health state)
        mana (:mana state)]
    (st/set-health health)
    (st/set-mana mana)
    (when (= 0 health)
      (pc/set-anim-int (st/get-model-entity) "health" 0)
      (fire :show-re-spawn-modal #(dispatch-pro :re-spawn))
      (poki/gameplay-stop)
      (js/setTimeout poki/commercial-break 2000))))

(let [skills-effects-before-response #{"heal" "cure" "breakDefense" "attackRange" "attackSingle"}
      temp-pos (pc/vec3)]
  (defn- process-world-snapshot [world]
    (doseq [s world
            :let [id (str (first s))
                  new-state (second s)
                  other-player (st/get-other-player id)
                  entity (j/get other-player :entity)
                  health (:health new-state)
                  new-anim-state (:st new-state)
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
          ;; TODO remove 'constantly' for prod
          (when-let [tw (j/get-in st/other-players [id :tween :interpolation])]
            (j/call (tw) :stop))

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
              (j/assoc-in! st/other-players [id :tween :interpolation] (constantly tween-interpolation))))
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
              nil))))
      (-> st/other-players
          (j/assoc-in! [id :prev-pos] new-pos)
          (j/assoc-in! [id :prev-state] new-anim-state)))))

(defn- process-effects [effects]
  (doseq [[e ids] effects
          id ids
          :when (not= id current-player-id)
          :let [other-player-state (st/get-other-player id)]]
    (case e
      :attack-r (effects/apply-effect-attack-r other-player-state)
      :attack-dagger (effects/apply-effect-attack-dagger other-player-state)
      :attack-one-hand (effects/apply-effect-attack-one-hand other-player-state)
      :attack-slow-down (effects/apply-effect-attack-slow-down other-player-state)
      :attack-single (effects/apply-effect-attack-flame other-player-state)
      :teleport (effects/apply-effect-teleport other-player-state)
      :hp-potion (effects/apply-effect-hp-potion other-player-state)
      :mp-potion (effects/apply-effect-mp-potion other-player-state)
      :fleet-foot (effects/apply-effect-fleet-foot other-player-state)
      :heal (effects/add-player-id-to-healed-ids id)
      :cure (effects/apply-effect-got-cure other-player-state)
      :break-defense (effects/apply-effect-got-defense-break other-player-state)
      :hide (j/call-in other-player-state [:skills :hide])
      :appear (j/call-in other-player-state [:skills :appear])
      :else (js/console.error "Unknown effect: " e))))

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
  (let [ws (:world-snapshot params)
        effects (:effects ws)
        kills (:kills ws)
        ws (dissoc ws :effects :kills)
        attack-ranges (get effects :attack-range)
        effects (dissoc effects :attack-range)]
    (set! world ws)
    (process-world-snapshot-for-player (get ws current-player-id))
    ;; TODO when tab is not focused, send that data to server and server makes the state idle for this player to other players
    (process-world-snapshot (dissoc ws current-player-id))
    (process-effects effects)
    (process-attack-ranges attack-ranges)
    (process-kills kills)
    (when-let [ids (seq @party-member-ids)]
      (fire :update-party-member-healths (reduce (fn [acc id]
                                                   (assoc acc id (get-in ws [id :health]))) {} ids)))))

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
  (js/setInterval
    ;; TODO online counter icin surekli atma, birileri girip cikinca at
    #(when (utils/tab-visible?)
       (dispatch-pro :ping))
    1000))

(defmethod dispatch-pro-response :ping [params]
  (fire :ui-update-ping (:ping params)))

(defmethod dispatch-pro-response :connect-to-world-state [params]
  (when (:connect-to-world-state params)
    (send-states-to-server)
    (dispatch-pro :request-all-players)
    (fire :ui-player-ready)
    (create-ping-interval)))

(defmethod dispatch-pro-response :request-all-players [params]
  (let [players (:request-all-players params)
        current-players-ids (set (cons current-player-id (seq (js/Object.keys st/other-players))))
        players (remove #(or (current-players-ids (:id %)) (nil? (:id %))) players)]
    (when (seq players)
      (fire :create-players players))))

(defn- on-ws-open []
  (dispatch-pro :get-server-stats))

(defn- on-ws-close []
  (fire :ui-init-modal-error "Connection closed! Please refresh the page.")
  (fire :ui-set-connection-lost))

(on :start-ws
    (fn []
      (connect {:url ws-url
                :on-message (fn [event]
                              (dispatch-pro-response (msg/unpack (j/get event :data))))
                :on-open (fn []
                           (println "WS connection established.")
                           (j/assoc! st/settings :ws-connected? true)
                           (reset! open? true)
                           (on-ws-open))
                :on-close (fn []
                            (j/assoc! st/settings
                                      :ws-connected? false
                                      :ws-closed? false)
                            (println "WS connection closed.")
                            (reset! open? false)
                            (on-ws-close))
                :on-error (fn []
                            (println "WS error occurred!")
                            (reset! open? false))})))

(on :init-game #(dispatch-pro :init %))
(on :connect-to-world-state #(dispatch-pro :connect-to-world-state))
(on :send-global-message #(dispatch-pro :send-global-message {:msg %}))
(on :send-party-message #(dispatch-pro :send-party-message {:msg %}))
(on :get-server-stats #(dispatch-pro :get-server-stats))
(on :get-score-board #(dispatch-pro :get-score-board))

(defmethod dispatch-pro-response :get-server-stats [params]
  (fire :ui-set-server-stats (:get-server-stats params)))

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
  (fire :ui-send-msg {:bp (:earned-bp params)}))

(let [re-spawn-error-msg {:re-spawn-error "Re-spawn failed. Try again."}]
  (defmethod dispatch-pro-response :re-spawn [params]
    (if (-> params :re-spawn :error)
      (fire :ui-send-msg re-spawn-error-msg)
      (let [pos (-> params :re-spawn :pos)
            health (-> params :re-spawn :health)
            mana (-> params :re-spawn :mana)]
        (st/move-player pos)
        (fire :close-re-spawn-modal)
        (fire :clear-all-cooldowns)
        (st/set-mana mana)
        (st/set-health health)
        (pc/set-anim-int (st/get-model-entity) "health" 100)))))

(comment
  (fire :start-ws)


  (send-states-to-server)
  (cancel-sending-states-to-server)

  )
