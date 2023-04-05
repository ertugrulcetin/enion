(ns enion-cljs.scene.network
  (:require
    [applied-science.js-interop :as j]
    [camel-snake-kebab.core :as csk]
    [cljs.reader :as reader]
    [clojure.string :as str]
    [enion-cljs.common :refer [dev? fire on dlog ws-url]]
    [enion-cljs.scene.entities.chest :as chest]
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

(defonce party-member-ids (volatile! []))

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
   (if (and @open? @socket (st/tab-visible?))
     (j/call @socket :send (msg/pack {:pro pro
                                      :data data
                                      :timestamp (js/Date.now)}))
     (js/console.error "Connection closed, can't send the payload!"))))

(defmulti dispatch-pro-response ffirst)

(defn- get-potions []
  (let [default {:hp-potions 10
                 :mp-potions 10}]
    (if-let [ls (common.utils/get-local-storage)]
      (try
        (let [potions (j/call ls :getItem "potions")]
          (if (str/blank? potions)
            default
            (reader/read-string potions)))
        (catch js/Error _
          default))
      default)))

(defn- get-tutorials []
  (let [default {}]
    (if-let [ls (common.utils/get-local-storage)]
      (try
        (let [potions (j/call ls :getItem "tutorials")]
          (if (str/blank? potions)
            default
            (reader/read-string potions)))
        (catch js/Error _
          default))
      default)))

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
              :server-full "Server is full. Please wait a bit and try again."
              :orc-race-full "Orc race is full. Please wait a bit and try again."
              :human-race-full "Human race is full. Please wait a bit and try again.")))
    (let [potions (get-potions)
          tutorials (get-tutorials)
          data (merge (:init params) potions {:tutorials tutorials})]
      (fire :close-init-modal)
      (fire :init data)
      (set! current-player-id (-> params :init :id))
      (fire :ui-init-game (assoc data :server-name @server-name))
      (fire :ui-init-tutorial-data data)
      (chest/register-chest-trigger-events (not (:what-is-the-first-quest? tutorials)))
      (poki/gameplay-start))))

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
        mana (:mana state)
        current-health (st/get-health)]
    (cond
      (and (= health 0) (not= 0 current-health))
      (do
        (st/set-health health)
        (st/set-mana mana)
        (pc/set-anim-int (st/get-model-entity) "health" 0)
        (fire :show-re-spawn-modal #(dispatch-pro :re-spawn))
        (poki/gameplay-stop))

      (not (and (= health 0) (= 0 current-health)))
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

(defn- process-effects [effects]
  (doseq [[e ids] effects
          id ids
          :when (not= id current-player-id)
          :let [other-player-state (st/get-other-player id)]]
    (case e
      :attack-r (effects/apply-effect-attack-r other-player-state)
      :attack-dagger (effects/apply-effect-attack-dagger other-player-state)
      :attack-stab (effects/apply-effect-attack-stab other-player-state)
      :attack-one-hand (effects/apply-effect-attack-one-hand other-player-state)
      :attack-slow-down (effects/apply-effect-attack-slow-down other-player-state)
      :attack-single (effects/apply-effect-attack-flame other-player-state)
      :attack-ice (effects/apply-effect-ice-spell other-player-state)
      :attack-priest (effects/apply-effect-attack-priest other-player-state)
      :attack-base (effects/apply-effect-attack-cauldron other-player-state)
      :teleport (effects/apply-effect-teleport other-player-state)
      :hp-potion (effects/apply-effect-hp-potion other-player-state)
      :mp-potion (effects/apply-effect-mp-potion other-player-state)
      :fleet-foot (effects/apply-effect-fleet-foot other-player-state)
      :heal (effects/add-player-id-to-healed-ids id)
      :cure (effects/apply-effect-got-cure other-player-state)
      :break-defense (effects/apply-effect-got-defense-break other-player-state)
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

(defonce npcs #js {})
(defonce npc (delay (pc/find-by-name "undead_warrior")))
(defonce npc-model (delay (pc/find-by-name @npc "undead_warrior_model")))
(defonce temp-v (pc/vec3))
(defonce temp-v2 (pc/vec3))
(defonce prev-state (volatile! :idle))
(defonce initial-pos #js {})
(defonce last-pos #js {})

(defn init-npcs []
  (doseq [i (range 4)
          :let [entity (pc/clone @npc)
                model (pc/find-by-name entity "undead_warrior_model")
                i (inc i)]]
    (pc/add-child (pc/root) entity)
    (j/assoc! npcs i (clj->js {:id i
                               :entity entity
                               :model model
                               :prev-state :idle
                               :initial-pos {}
                               :last-pos {}
                               :from (pc/vec3)
                               :to (pc/vec3)}))))

(comment
  (init-npcs)
  npcs
  (js/console.log (j/get-in npcs [0 :entity]))
  (j/get-in npcs [0 :entity])
  (js/console.log (j/get-in npcs [0 :entity]))
  (pc/get-pos (j/get-in npcs [0 :entity]))
  )

(defn- interpolate-npc [npc-id new-x new-y new-z]
  (let [npc-entity (j/get-in npcs [npc-id :entity])
        initial-pos (j/get-in npcs [npc-id :initial-pos])
        last-pos (j/get-in npcs [npc-id :last-pos])
        current-pos (pc/get-pos npc-entity)
        current-x (j/get current-pos :x)
        current-y (j/get current-pos :y)
        current-z (j/get current-pos :z)]
    (when (and current-x new-x current-z new-z
               (or (not= (common.utils/parse-float current-x 2)
                         (common.utils/parse-float new-x 2))
                   (not= (common.utils/parse-float current-z 2)
                         (common.utils/parse-float new-z 2))))
      (let [tween-interpolation (-> (j/call npc-entity :tween initial-pos)
                                    (j/call :to last-pos 0.05 pc/linear))]
        (j/assoc! initial-pos :x current-x :y current-y :z current-z)
        (j/assoc! last-pos :x new-x :y new-y :z new-z)
        (j/call tween-interpolation :on "update"
                (fn []
                  (let [x (j/get initial-pos :x)
                        y (j/get initial-pos :y)
                        z (j/get initial-pos :z)]
                    (pc/set-pos npc-entity x y z))))
        (j/call tween-interpolation :start)))))

(defn- filter-hit-by-terrain [result]
  (when (= "terrain" (j/get-in result [:entity :name]))
    result))

(defn- get-closest-hit-of-terrain [result]
  (j/get-in result [:point :y]))

(defn- update-npc-anim-state [model state prev-state]
  (when (not= state prev-state)
    (pc/set-anim-boolean model (csk/->camelCaseString prev-state) false)
    (pc/set-anim-boolean model (csk/->camelCaseString state) true)))

;; Skeleton Warrior
;; Bone Fighter
;; Skull Knight
;; Undead Soldier
;; Skeletal Champion
(defn- process-npcs [npcs-world-state]
  (doseq [npc npcs-world-state
          :let [npc-id (:id npc)
                npc-entity (j/get-in npcs [npc-id :entity])]
          :when npc-entity]
    (let [model (j/get-in npcs [npc-id :model])
          state (:state npc)
          new-x (:px npc)
          new-z (:pz npc)
          from (j/get-in npcs [npc-id :from])
          to (j/get-in npcs [npc-id :to])
          _ (pc/setv from new-x 10 new-z)
          _ (pc/setv to new-x -1 new-z)
          hit (->> (pc/raycast-all from to)
                   (filter filter-hit-by-terrain)
                   (sort-by get-closest-hit-of-terrain)
                   first)
          new-y (or (some-> hit (j/get-in [:point :y])) 0.55)
          target-player-id (:target-player-id npc)
          target-player-pos (when target-player-id
                              (if (= current-player-id target-player-id)
                                (st/get-pos)
                                (st/get-pos target-player-id)))
          prev-state (j/get-in npcs [npc-id :prev-state])]
      (update-npc-anim-state model state prev-state)
      (when-not (= state :idle)
        (if target-player-id
          (pc/look-at model (j/get target-player-pos :x) new-y (j/get target-player-pos :z) true)
          (pc/look-at model new-x new-y new-z true)))
      (j/assoc-in! npcs [npc-id :prev-state] state)
      (interpolate-npc npc-id new-x new-y new-z))))

(defmethod dispatch-pro-response :world-snapshot [params]
  (let [ws (:world-snapshot params)
        effects (:effects ws)
        kills (:kills ws)
        members-with-break-defense (:break-defense ws)
        npcs (:npcs ws)
        ws (dissoc ws :effects :kills :break-defense :npcs)
        attack-ranges (get effects :attack-range)
        effects (dissoc effects :attack-range)]
    (set! world ws)
    (process-world-snapshot-for-player (get ws current-player-id))
    (process-world-snapshot (dissoc ws current-player-id))
    (process-effects effects)
    (process-attack-ranges attack-ranges)
    (process-kills kills)
    (process-npcs npcs)
    (when-let [ids (seq @party-member-ids)]
      (fire :update-party-members {:healths (reduce (fn [acc id]
                                                      (assoc acc id (get-in ws [id :health]))) {} ids)
                                   :members-with-break-defense members-with-break-defense}))))

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

(defmethod dispatch-pro-response :connect-to-world-state [params]
  (when (:connect-to-world-state params)
    (let [tutorials (get-tutorials)
          how-to-navigate? (:how-to-navigate? tutorials)]
      (send-states-to-server)
      (dispatch-pro :request-all-players)
      (fire :ui-player-ready)
      (create-ping-interval)
      (when-not how-to-navigate?
        (fire :ui-start-navigation-steps)
        (utils/set-item "tutorials" (pr-str (assoc tutorials :how-to-navigate? true)))))))

(defmethod dispatch-pro-response :request-all-players [params]
  (let [players (:request-all-players params)
        current-players-ids (set (cons current-player-id (seq (js/Object.keys st/other-players))))
        players (remove #(or (current-players-ids (:id %)) (nil? (:id %))) players)]
    (when (seq players)
      (fire :create-players players))))

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
      (connect {:url ws-url
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
  (fire :ui-send-msg {:bp (:earned-bp params)}))

(let [re-spawn-error-msg {:re-spawn-error "Re-spawn failed. Try again."}]
  (defmethod dispatch-pro-response :re-spawn [params]
    (if (-> params :re-spawn :error)
      (fire :ui-send-msg re-spawn-error-msg)
      (let [pos (-> params :re-spawn :pos)
            health (-> params :re-spawn :health)
            mana (-> params :re-spawn :mana)]
        (when (st/asas?)
          (j/call-in st/player [:skills :appear]))
        (st/move-player pos)
        (j/assoc! st/player :slow-down? false :speed st/speed)
        (fire :ui-re-spawn)
        (st/set-mana mana)
        (st/set-health health)
        (pc/set-anim-int (st/get-model-entity) "health" 100)
        (st/cancel-target-pos)
        (poki/gameplay-start)))))

(on :close-socket-for-re-init
    (fn []
      (set! effects/healed-player-ids (js/Set.))
      (vreset! party-member-ids [])
      (poki/gameplay-stop)
      (poki/commercial-break)
      (some-> @socket (j/call :close 3001 "re-init"))
      (cancel-sending-states-to-server)
      (cancel-ping-interval)))
