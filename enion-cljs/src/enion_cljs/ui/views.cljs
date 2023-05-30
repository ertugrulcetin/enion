(ns enion-cljs.ui.views
  (:require
    ["bad-words" :as bad-words]
    ["react-device-detect" :as device-dec]
    [applied-science.js-interop :as j]
    [breaking-point.core :as bp]
    [common.enion.item :as item]
    [common.enion.skills :as common.skills]
    [enion-cljs.common :refer [fire on dev? dlog]]
    [enion-cljs.ui.events :as events]
    [enion-cljs.ui.intro :as intro]
    [enion-cljs.ui.shop :as shop]
    [enion-cljs.ui.styles :as styles]
    [enion-cljs.ui.subs :as subs]
    [enion-cljs.ui.utils :as ui.utils :refer [img->img-url]]
    [enion-cljs.utils :as common.utils]
    [re-frame.core :refer [subscribe dispatch dispatch-sync]]
    [reagent.core :as r]))

(def mouse-x (r/atom nil))
(def mouse-y (r/atom nil))

(def how-often-to-show-ad 2)
(def death-count (atom 0))

(defonce game-init? (r/atom false))

(defn skill->img [skill]
  (case skill
    "attackOneHand" (img->img-url "attack_one_hand.png")
    "attackSlowDown" (img->img-url "attack_slowdown.png")
    "battleFury" (img->img-url "attack_boost.png")
    "shieldWall" (img->img-url "shield_wall.png")
    "fleetFoot" (img->img-url "fleet_foot.png")
    "attackDagger" (img->img-url "attack_dagger.png")
    "attackStab" (img->img-url "attack_stab.png")
    "phantomVision" (img->img-url "phantom_vision.png")
    "hide" (img->img-url "hide.png")
    "attackRange" (img->img-url "attack_range.jpeg")
    "attackSingle" (img->img-url "attack_single.png")
    "attackIce" (img->img-url "attack_ice.png")
    "attackPriest" (img->img-url "attack_priest.png")
    "teleport" (img->img-url "teleport.png")
    "heal" (img->img-url "heal.png")
    "cure" (img->img-url "cure.png")
    "breakDefense" (img->img-url "break_defense.png")
    "hpPotion" (img->img-url "hp.png")
    "mpPotion" (img->img-url "mp.png")))

(defn temp-skill-img []
  (when-let [skill-move @(subscribe [::subs/skill-move])]
    [:<>
     [:span
      {:style {:position :absolute
               :top (- @mouse-y 65)
               :left @mouse-x}
       :class (styles/temp-skill-order-description)}
      "You're updating the skill order. Click on any spot where you would like to place this skill."]
     [:div
      {:style {:position :absolute
               :top @mouse-y
               :left @mouse-x
               :z-index 15
               :pointer-events :none}}
      [:img
       {:class (styles/skill-img false false)
        :src (skill->img (:skill skill-move))}]]]))

(defn skill-description []
  (let [offset-width (r/atom 0)
        offset-height (r/atom 0)]
    (fn []
      (when-let [{:keys [name description cooldown required-mana required-level]} (and (nil? @(subscribe [::subs/skill-move]))
                                                                                       @(subscribe [::subs/skill-description]))]
        (let [width @(subscribe [::bp/screen-width])
              offset-top (if (< width 1250) -15 5)]
          [:div
           {:style {:position :absolute
                    :top (- @mouse-y @offset-height offset-top)
                    :left (- @mouse-x (/ @offset-width 2))
                    :z-index 15
                    :pointer-events :none}}
           [:div
            {:ref #(do
                     (when-let [ow (j/get % :offsetWidth)]
                       (reset! offset-width ow))
                     (when-let [oh (j/get % :offsetHeight)]
                       (reset! offset-height oh)))
             :class (styles/skill-description)}
            [:span.skill-name name]
            [:span.desc description]
            [:span.info "Cooldown: " (/ cooldown 1000) "s"]
            [:br]
            (when required-mana
              [:span.info "Required MP: " required-mana])
            (when required-level
              [:<>
               [:br]
               [:span.info "Required Level: " required-level]])]])))))

(defn- cooldown [skill]
  (let [cooldown-secs (-> skill common.skills/skills :cooldown)]
    (r/create-class
      {:component-did-mount
       (fn []
         (let [id (js/setTimeout
                    (fn []
                      (dispatch-sync [::events/clear-cooldown skill]))
                    cooldown-secs)]
           (dispatch-sync [::events/set-cooldown-timeout-id id skill])))
       :component-will-unmount #(dispatch-sync [::events/clear-cooldown skill])
       :reagent-render
       (fn []
         [:div (styles/cooldown (/ cooldown-secs 1000))])})))

(defn- skill [_ _]
  (let [event-listeners (atom {})]
    (fn [index skill]
      (let [skill-move @(subscribe [::subs/skill-move])
            hp-potions @(subscribe [::subs/hp-potions])
            mp-potions @(subscribe [::subs/mp-potions])
            level @(subscribe [::subs/level])]
        [:div {:id (str "skill-" skill)
               :ref (fn [ref]
                      (when ref
                        (when-let [[ref f] (get @event-listeners skill)]
                          (.removeEventListener ref "contextmenu" f false))
                        (let [f (fn [ev]
                                  (.preventDefault ev)
                                  (dispatch [::events/update-skills-order index skill])
                                  false)]
                          (.addEventListener ref "contextmenu" f false)
                          (swap! event-listeners assoc skill [ref f]))))
               :class (styles/skill)
               :on-click (fn []
                           (dispatch [::events/show-skill-description nil])
                           (if skill-move
                             (dispatch [::events/update-skills-order index skill])
                             (let [event (js/KeyboardEvent. "keydown" #js {:code (str "Digit" (inc index))
                                                                           :key (inc index)
                                                                           :keyCode (+ 49 index)
                                                                           :bubbles true
                                                                           :cancelable true})
                                   event #js {:event event
                                              :key (+ 49 index)}]
                               (fire :process-skills-from-an-event event))))
               :on-mouse-enter #(dispatch [::events/show-skill-description skill])
               :on-mouse-leave #(dispatch [::events/show-skill-description nil])}
         [:span (styles/skill-number) (inc index)]
         (when (= "hpPotion" skill)
           [:span (styles/potion-count) hp-potions])
         (when (= "mpPotion" skill)
           [:span (styles/potion-count) mp-potions])
         (when-not (= :none skill)
           [:div (styles/childs-overlayed)
            [:img {:class (styles/skill-img
                            (or @(subscribe [::subs/blocked-skill? skill])
                                @(subscribe [::subs/died?]))
                            @(subscribe [::subs/not-enough-mana-or-level? skill level]))
                   :src (skill->img skill)}]
            (when @(subscribe [::subs/cooldown-in-progress? skill])
              [cooldown skill])])]))))

(defn- hp-bar []
  (let [health @(subscribe [::subs/health])
        total-health @(subscribe [::subs/total-health])
        health-perc (/ (* 100 health) total-health)]
    [:div (styles/hp-bar)
     [:div (styles/hp-hit health-perc)]
     [:div (styles/hp health-perc @(subscribe [::subs/defense-break?]))]
     [:span (styles/hp-mp-text) (str health "/" total-health)]]))

(defn- mp-bar []
  (let [mana @(subscribe [::subs/mana])
        total-mana @(subscribe [::subs/total-mana])
        mana-perc (/ (* 100 mana) total-mana)]
    [:div (styles/mp-bar)
     [:div (styles/mp-used mana-perc)]
     [:div (styles/mp mana-perc)]
     [:span (styles/hp-mp-text) (str mana "/" total-mana)]]))

(defn- exp-bar []
  (let [exp @(subscribe [::subs/exp])
        required-exp @(subscribe [::subs/required-exp])
        exp-perc (/ (* 100 exp) required-exp)]
    [:div (styles/exp-bar)
     [:div (styles/exp-hit exp-perc)]
     [:div (styles/exp exp-perc)]
     [:span (styles/hp-mp-text) (str (common.utils/parse-float exp-perc 2) "%")]]))

(defn- player-name []
  [:div (styles/player-name-container)
   [:span @(subscribe [::subs/username])]])

(defn- hp-mp-bars []
  [:div (styles/hp-mp-container)
   [hp-bar]
   [mp-bar]
   [exp-bar]])

(defn- party-member-hp-bar [health total-health break-defense?]
  (let [health-perc (/ (* 100 health) total-health)]
    [:div (styles/party-member-hp-bar)
     [:div (styles/party-member-hp-hit health-perc)]
     [:div (styles/party-member-hp health-perc break-defense?)]]))

(defn- party-member-hp-mp-bars [id username health total-health break-defense?]
  [:div
   {:class (styles/party-member-hp-mp-container (= id @(subscribe [::subs/selected-party-member])))
    :on-click #(do
                 (fire :select-party-member id)
                 (dispatch [::events/select-party-member id]))}
   [party-member-hp-bar health total-health break-defense?]
   [:span (styles/party-member-username) username]])

(defn- skill-bar []
  [:div#skill-bar (styles/skill-bar)
   (map-indexed
     (fn [i s]
       ^{:key (str s "-" i)}
       [skill i s])
     @(subscribe [::subs/skills]))])

(defn- create-ad-button [_]
  (let [f (fn [e]
            (when (= "Space" (j/get e :code))
              (.preventDefault e)))
        ref (atom nil)]
    (r/create-class
      {:component-did-mount (fn [] (some-> @ref (.addEventListener "keydown" f false)))
       :component-will-unmount (fn [] (some-> @ref (.removeEventListener "keydown" f false)))
       :reagent-render
       (fn [{:keys [class on-click text disabled]}]
         [:button
          {:ref #(reset! ref %)
           :disabled disabled
           :class class
           :on-click on-click}
          text])})))

(defn- get-hp-&-mp-potions-ad []
  [create-ad-button {:class (styles/get-hp-mp-potions-for-free)
                     :on-click #(dispatch [::events/show-ad :rewarded-break-potions])
                     :text "Get Hp & Mp potions for Free! \uD83C\uDFAC"}])

(defn- actions-section []
  [:div (styles/actions-container)
   (when @(subscribe [::subs/show-hp-mp-potions-ads-button?])
     [get-hp-&-mp-potions-ad])
   ;; [player-name]
   [hp-mp-bars]
   [skill-bar]])

(defn- chat-message [msg input-active?]
  (let [killer (:killer msg)
        killer-race (:killer-race msg)
        killed (:killed msg)]
    ;; TODO fix here
    @(subscribe [::subs/current-time])
    [:div
     {:style {:visibility (cond
                            input-active? "visible"
                            (<= (- (js/Date.now) (:created-at msg)) 10000) "visible"
                            :else "hidden")}}
     (if killer
       [:div
        [:strong
         {:class (if (= "orc" killer-race) "orc-defeats" "human-defeats")}
         (str killer " defeated " killed "")]
        [:br]]
       [:div (styles/chat-message input-active?)
        (if (= "System" (:from msg))
          [:strong (:text msg)]
          [:<>
           [:strong (str (:from msg) ":")]
           [:span (:text msg)]])
        [:br]])]))

(defn scroll-to-bottom [e]
  (j/assoc! e :scrollTop (j/get e :scrollHeight)))

(defn- on-message-box-update
  ([ref]
   (on-message-box-update ref false))
  ([ref info?]
   (when-let [elem @ref]
     (scroll-to-bottom elem)
     #_(if info?
         (let [gap (- (j/get elem :scrollHeight) (+ (j/get elem :scrollTop)
                                                    (j/get elem :offsetHeight)))]
           (when (< gap 50)
             (scroll-to-bottom elem)))
         (scroll-to-bottom elem)))))

(defn- chat-message-box [_]
  (let [ref (atom nil)]
    (r/create-class
      {:component-did-mount (fn [] (some-> @ref scroll-to-bottom))
       :component-did-update #(on-message-box-update ref)
       :reagent-render (fn [input-active?]
                         [:div
                          {:ref #(some->> % (reset! ref))
                           :class (styles/message-box)}
                          (for [[idx msg] (map-indexed vector @(subscribe [::subs/chat-messages]))]
                            ^{:key idx}
                            [chat-message msg input-active?])])})))

(defn- chat-input [_]
  (let [ref (atom nil)]
    (r/create-class
      {:component-did-update (fn [this old]
                               (let [old-value (second old)
                                     new-value (second (r/argv this))]
                                 (when (and (not= old-value new-value) @ref)
                                   (if new-value
                                     (.focus @ref)
                                     (.blur @ref)))))
       :reagent-render
       (fn [input-active?]
         [:input
          {:ref #(some->> % (reset! ref))
           :value @(subscribe [::subs/chat-message])
           :disabled (not input-active?)
           :placeholder "Press ENTER to chat!"
           :class (styles/chat-input input-active?)
           :on-change #(dispatch-sync [::events/set-chat-message (-> % .-target .-value)])
           :max-length 60}])})))

(defonce message-expiration-started? (atom false))

(defn- chat []
  (r/create-class
    {:component-did-mount (fn []
                            (when-not @message-expiration-started?
                              (js/setInterval #(dispatch [::events/update-current-time]) 1000)
                              (reset! message-expiration-started? true)))
     :reagent-render
     (fn []
       (let [input-active? @(subscribe [::subs/chat-input-active?])]
         [:div
          {:class (styles/chat-wrapper)
           :on-mouse-over #(fire :on-ui-element? true)
           :on-mouse-out #(fire :on-ui-element? false)}
          [:div (styles/chat)
           [chat-message-box input-active?]
           [chat-input input-active?]]]))}))

(defn inventory-slots []
  (let [inventory @(subscribe [::subs/inventory])
        scroll-selected? @(subscribe [::subs/scroll-selected?])]
    (for [i (range item/inventory-max-size)
          :let [item (get inventory i)]]
      ^{:key i}
      [:div {:class (styles/inventory-square (and scroll-selected?
                                                  (:item item)
                                                  (not= :scroll (get-in item/items [(:item item) :type]))))
             :on-mouse-enter #(dispatch [::events/show-item-description item])
             :on-mouse-leave #(dispatch [::events/show-item-description nil])
             :on-click #(do
                          (dispatch [::events/select-item-in-inventory i item])
                          (dispatch [::events/show-item-description nil]))}
       (when item
         [:img {:src (img->img-url (str "item/" (-> item :item item/items :img)))}])])))

(defn- char-name []
  [:tr
   [:td {:class [(styles/char-info-cell :left) (styles/char-info-cell-top)]} "Name"]
   [:td {:class [(styles/char-info-cell :right) (styles/char-info-cell-top)]} @(subscribe [::subs/username])]])

(defn- char-race []
  [:tr
   [:td {:class (styles/char-info-cell :left)} "Race"]
   [:td {:class (styles/char-info-cell :right)} (if (= "orc" @(subscribe [::subs/race])) "Orc" "Human")]])

(defn- char-class []
  [:tr
   [:td {:class (styles/char-info-cell :left)} "Class"]
   [:td {:class (styles/char-info-cell :right)} (case @(subscribe [::subs/class])
                                                  "mage" "Mage"
                                                  "warrior" "Warrior"
                                                  "asas" "Assassin"
                                                  "priest" "Priest")]])

(defn- char-ap []
  [:tr
   [:td {:class (styles/char-info-cell :left)} "Attack Power"]
   [:td {:class (styles/char-info-cell :right)} @(subscribe [::subs/attack-power])]])

(defn- char-level []
  [:tr
   [:td {:class (styles/char-info-cell :left)} "Level"]
   [:td {:class (styles/char-info-cell :right)} @(subscribe [::subs/level])]])

(defn- char-exp []
  [:tr
   [:td {:class (styles/char-info-cell :left)} "Experience Points"]
   [:td {:class (styles/char-info-cell :right)} (str @(subscribe [::subs/exp]) "/" @(subscribe [::subs/required-exp]))]])

(defn- char-bp []
  [:tr
   [:td {:class (styles/char-info-cell :left)} "Battle Points"]
   [:td {:class (styles/char-info-cell :right)} @(subscribe [::subs/bp])]])

(defn- char-coins []
  [:tr
   [:td {:class (styles/char-info-cell :left)} "Coins"]
   [:td {:class (styles/char-info-cell :right)} @(subscribe [::subs/coin-str])]])

(defn temp-selected-item-img []
  (when-let [item @(subscribe [::subs/selected-inventory-item])]
    [:div
     {:style {:position :absolute
              :top @mouse-y
              :left @mouse-x
              :z-index 15
              :pointer-events :none}}
     [:img
      {:class (styles/skill-img false false)
       :src (img->img-url (str "item/" (-> item :item item/items :img)))}]]))

(defn- inventory []
  [:<>
   [:div (styles/char-panel-header)
    [:span "Inventory"]]
   [:div {:class (styles/inventory-wrapper)}
    [:div {:class (styles/inventory-container)}
     (inventory-slots)]]
   [temp-selected-item-img]])

(defn upgrade-item-modal [{:keys [item]}]
  [:div (styles/buy-item-modal)
   [:p "Do you want to upgrade " [:b (str (get-in item/items [(:item item) :name]) " (+" (-> item :level) ")")] "?"]
   [:p "(You could lose the item if the upgrade fails!)"]
   [:div (styles/party-request-buttons-container)
    [:button
     {:class (styles/buy-item-button-accept)
      :on-click #(dispatch [::events/upgrade-item])}
     "Yes"]
    [:button
     {:class (styles/buy-item-button-reject)
      :on-click #(dispatch [::events/cancel-upgrade-item])}
     "No"]]])

(defn character-panel []
  (when @(subscribe [::subs/char-panel-open?])
    [:<>
     [:div {:class (styles/char-panel)
            :on-mouse-over #(fire :on-ui-element? true)
            :on-mouse-out #(fire :on-ui-element? false)}
      [:div {:class (styles/char-panel-container)}
       [:table {:class (styles/char-info-table)}
        [:thead
         [char-name]
         [char-race]
         [char-class]
         [char-ap]
         [char-level]
         [char-exp]
         [char-bp]
         [char-coins]]]
       [:div (styles/char-panel-header)
        [:span "Equipment"]]
       [:div {:class (styles/equip)}
        [:div {:class (styles/equip-square)
               :on-click #(dispatch [::events/equip :weapon])
               :on-mouse-enter #(dispatch [::events/show-item-description @(subscribe [::subs/equip :weapon])])
               :on-mouse-leave #(dispatch [::events/show-item-description nil])}
         (if-let [{:keys [item]} @(subscribe [::subs/equip :weapon])]
           [:img {:src (img->img-url (str "item/" (get-in item/items [item :img])))}]
           [:img {:src (img->img-url "equip/weapon.png")}])]
        [:div {:class (styles/equip-square)
               :on-click #(dispatch [::events/equip :shield])
               :on-mouse-enter #(dispatch [::events/show-item-description @(subscribe [::subs/equip :shield])])
               :on-mouse-leave #(dispatch [::events/show-item-description nil])}
         (if-let [{:keys [item]} @(subscribe [::subs/equip :shield])]
           [:img {:src (img->img-url (str "item/" (get-in item/items [item :img])))}]
           [:img {:src (img->img-url "equip/shield.png")}])]
        [:div {:class (styles/equip-square)
               :on-click #(dispatch [::events/equip])}
         [:img {:src (img->img-url "equip/ring.png")}]]
        ;; [:div {:class (styles/equip-square)}]
        ;; [:div {:class (styles/equip-square)}]
        ;; [:div {:class (styles/equip-square)}]
        ]
       [inventory]]]
     [shop/item-description {:inventory? true
                             :selected-inventory-item @(subscribe [::subs/selected-inventory-item])}]]))

(defn- selected-player []
  (when-let [{:keys [username health enemy? npc? level]} @(subscribe [::subs/selected-player])]
    [:div (styles/selected-player)
     [:span (styles/selected-player-text enemy?)
      username]
     [:div (styles/hp-bar-selected-player)
      [:div (styles/hp-hit health)]
      [:div (styles/hp health nil)]]
     (when npc?
       [:span (styles/selected-player-lvl-text) (str "Level " level)])]))

(defonce x (r/atom 0))
(defonce z (r/atom 0))
(defonce anim-state (atom "idle"))

;; Math formula of map
;; scale = Map zoom size (#holder) / terrain size (100) ratio
;; gap = (Map zoom size (#holder) - Minimap size) / 2
;; (+ (* (j/get pos :x) scale) gap)

;; (when-not (= "idle" (player/get-state))
;;       (let [pos (player/get-position)]
;;         (reset! x (+ (* (j/get pos :x) 5) 175))
;;         (reset! y (+ (* (j/get pos :z) 5) 175))))
(defn- map-holder []
  [:div
   {:class (styles/holder)
    :style {:left (str (- @x) "px")
            :top (str (- @z) "px")}}
   [:img
    {:class (styles/minimap-img)
     :src "img/minimap.png"}]])

(defn- minimap []
  (let [interval-id (atom nil)]
    (r/create-class
      {:component-did-mount (fn []
                              (on :state-for-minimap-response
                                  (fn [state]
                                    (reset! anim-state (j/get state :anim-state))
                                    (reset! x (+ (* (j/get state :x) 5) 175))
                                    (reset! z (+ (* (j/get state :z) 5) 175))))
                              (reset! interval-id (js/setInterval
                                                    (fn []
                                                      (when-not (= "idle" @anim-state)
                                                        (reset! x (+ (* @x 5) 175))
                                                        (reset! z (+ (* @z 5) 175)))
                                                      (fire :state-for-minimap))
                                                    250)))
       :component-will-unmount (fn []
                                 (when-let [id @interval-id]
                                   (js/clearInterval id)))
       :reagent-render (fn []
                         [:div (styles/minimap)
                          [:div (styles/map-overflow)
                           [map-holder]
                           [:div (styles/minimap-player)]]])})))

;; write a function that takes callback as an argument, and creates event listener on document with addEventListener
;; when user pressing and holding ESC key at the sametime for 0.5 seconds, call the callback
#_(defn- on-esc-pressed-for-1-5-seconds [callback]
    (let [esc-key-code 27
          esc-key-pressed? (atom false)
          esc-key-pressed-timeout-id (atom nil)]
      (j/call js/document :addEventListener "keydown"
              (fn [e]
                (when (= esc-key-code (.-keyCode e))
                  (reset! esc-key-pressed? true)
                  (reset! esc-key-pressed-timeout-id
                          (js/setTimeout
                            (fn []
                              (when @esc-key-pressed?
                                (callback)
                                (reset! esc-key-pressed? false)))
                            500)))))
      (j/call js/document :addEventListener "keyup"
              (fn [e]
                (when (= esc-key-code (.-keyCode e))
                  (reset! esc-key-pressed? false)
                  (js/clearTimeout @esc-key-pressed-timeout-id))))))

(defn- party-request-modal* [username on-accept on-reject time]
  (let [party-request-duration (- common.skills/party-request-duration-in-milli-secs 1000)
        countdown-seconds (r/atom (/ party-request-duration 1000))
        interval-id (atom nil)]
    (r/create-class
      {:component-did-mount (fn []
                              (let [interval-id* (js/setInterval
                                                   (fn []
                                                     (let [result (int (/ (- (+ time party-request-duration) (js/Date.now)) 1000))
                                                           result (if (<= result 0) 0 result)]
                                                       (reset! countdown-seconds result)))
                                                   500)]
                                (reset! interval-id interval-id*)))
       :component-did-update (fn []
                               (when (= 0 @countdown-seconds)
                                 (some-> @interval-id js/clearInterval)
                                 (dispatch [::events/close-party-request-modal])))
       :reagent-render (fn []
                         [:div (styles/party-request-modal)
                          [:p (str "Do you want to join " username "'s party?")] [:p (str "Seconds remaining: " @countdown-seconds)]
                          [:div (styles/party-request-buttons-container)
                           [:button
                            {:class (styles/party-request-accept-button)
                             :on-click on-accept}
                            "Accept"]
                           [:button
                            {:class (styles/party-request-reject-button)
                             :on-click on-reject}
                            "Reject"]]])})))

(defn party-request-modal []
  (let [{:keys [open? username on-accept on-reject time]} @(subscribe [::subs/party-request-modal])]
    (when open?
      [party-request-modal* username on-accept on-reject time])))

(defn- re-spawn-modal* [on-ok time]
  (let [re-spawn-duration (+ common.skills/re-spawn-duration-in-milli-secs 1000)
        countdown-seconds (r/atom (/ re-spawn-duration 1000))
        interval-id (atom nil)]
    (r/create-class
      {:component-did-mount (fn []
                              (let [interval-id* (js/setInterval
                                                   (fn []
                                                     (let [result (int (/ (- (+ time re-spawn-duration) (js/Date.now)) 1000))
                                                           result (if (<= result 0) 0 result)]
                                                       (reset! countdown-seconds result)))
                                                   500)]
                                (reset! interval-id interval-id*)
                                (swap! death-count inc)
                                (when (not= (mod @death-count how-often-to-show-ad) 0)
                                  (fire :commercial-break))))
       :component-did-update (fn []
                               (when (= 0 @countdown-seconds)
                                 (some-> @interval-id js/clearInterval)))
       :reagent-render (fn []
                         [:div (styles/re-spawn-modal)
                          (if (> @countdown-seconds 0)
                            [:p (str "You need to wait " (int @countdown-seconds) " seconds to re-spawn")]
                            [:p "Press OK to return to the respawn point"])
                          (when-not (> @countdown-seconds 0)
                            [:div (styles/re-spawn-button-container)
                             [:button
                              {:disabled (> @countdown-seconds 0)
                               :class (styles/re-spawn-button)
                               :on-click #(do
                                            (js/clearInterval @interval-id)
                                            (on-ok))}
                              "OK"]
                             (when (and (= (mod @death-count how-often-to-show-ad) 0)
                                        (not (> @countdown-seconds 0)))
                               [:<>
                                [:p "Or watch an Ad to respawn here"]
                                [create-ad-button {:class (styles/re-spawn-ad-button)
                                                   :on-click #(do
                                                                (js/clearInterval @interval-id)
                                                                (dispatch [::events/show-ad :rewarded-break-re-spawn]))
                                                   :text "Watch Ad to respawn here! \uD83C\uDFAC"}]])])])})))

(defn re-spawn-modal []
  (let [{:keys [open? on-ok time]} @(subscribe [::subs/re-spawn-modal])]
    (when open?
      [re-spawn-modal* on-ok time])))

(defn- display-class [class]
  (case class
    "warrior" "Warrior"
    "priest" "Priest"
    "asas" "Assassin"
    "mage" "Mage"))

(defn- orc-row [orc]
  [:<>
   [:td (styles/score-modal-orc-color) (:username orc)]
   [:td (styles/score-modal-orc-color) (:level orc)]
   [:td (styles/score-modal-orc-color) (display-class (:class orc))]
   [:td (styles/score-modal-orc-color) (:bp orc)]])

(defn- human-row [human]
  [:<>
   [:td (styles/score-modal-human-color) (:username human)]
   [:td (styles/score-modal-human-color) (:level human)]
   [:td (styles/score-modal-human-color) (display-class (:class human))]
   [:td (styles/score-modal-human-color) (:bp human)]])

(defn- orc-human-row [orc human]
  [:tr
   (if orc [orc-row orc] [:<> [:td] [:td] [:td] [:td]])
   (if human [human-row human] [:<> [:td] [:td] [:td] [:td]])])

(defn- score-modal []
  (r/create-class
    {:component-did-mount #(fire :get-score-board)
     :component-will-unmount #(fire :on-ui-element? false)
     :reagent-render
     (fn []
       [:div
        {:class (styles/score-modal)
         :on-mouse-over #(fire :on-ui-element? true)
         :on-mouse-out #(fire :on-ui-element? false)}
        [:div
         {:style {:display "flex"
                  :justify-content "center"}}
         [:table
          [:thead
           [:tr
            [:th
             {:colSpan "4"
              :style {:color styles/orc-color}}
             "Orcs"]
            [:th {:colSpan "4"
                  :style {:color styles/human-color}} "Humans"]]
           [:tr
            [:th (styles/score-modal-orc-color) "Player"]
            [:th (styles/score-modal-orc-color) "Level"]
            [:th (styles/score-modal-orc-color) "Class"]
            [:th (styles/score-modal-orc-color) "Battle Points"]
            [:th (styles/score-modal-human-color) "Player"]
            [:th (styles/score-modal-human-color) "Level"]
            [:th (styles/score-modal-human-color) "Class"]
            [:th (styles/score-modal-human-color) "Battle Points"]]]
          [:tbody
           (let [players @(subscribe [::subs/score-board])
                 orcs (sort-by :bp > (filter #(= "orc" (:race %)) players))
                 humans (sort-by :bp > (filter #(= "human" (:race %)) players))
                 orcs-count (count orcs)
                 humans-count (count humans)
                 same-count? (= orcs-count humans-count)
                 orcs-greater? (> orcs-count humans-count)
                 result (cond
                          same-count? (interleave orcs humans)
                          orcs-greater? (interleave orcs (concat humans (repeat (- orcs-count humans-count) nil)))
                          :else (interleave (concat orcs (repeat (- humans-count orcs-count) nil)) humans))]
             (for [[orc human] (partition-all 2 result)]
               ^{:key (str (:id orc) "-" (:id human))}
               [orc-human-row orc human]))]]]])}))

(defn- party-list []
  [:div {:class (styles/party-list-container @(subscribe [::subs/minimap?]))
         :on-mouse-over #(fire :on-ui-element? true)
         :on-mouse-out #(fire :on-ui-element? false)}
   [:div
    {:id "party"
     :class (styles/party-action-button-container)}
    (let [selected-party-member @(subscribe [::subs/selected-party-member])]
      (cond
        @(subscribe [::subs/party-leader-selected-himself?])
        [:button {:class (styles/party-action-button)
                  :on-click #(fire :exit-from-party)}
         "Cancel party"]

        @(subscribe [::subs/party-leader-selected-member?])
        [:button {:class (styles/party-action-button)
                  :on-click #(fire :remove-from-party selected-party-member)}
         "Remove from party"]

        @(subscribe [::subs/able-to-add-party-member?])
        [:button {:class (styles/party-action-button)
                  :on-click #(fire :add-to-party)}
         "Add to party"]

        @(subscribe [::subs/able-to-exit-from-party?])
        [:button {:class (styles/party-action-button)
                  :on-click #(fire :exit-from-party)}
         "Exit from party"]))]
   (for [{:keys [id username health total-health break-defense?]} @(subscribe [::subs/party-members])]
     ^{:key id}
     [party-member-hp-mp-bars id username health total-health break-defense?])])

;; TODO add general component that prevents space press to enable button
(defn- settings-button []
  (let [minimap-open? @(subscribe [::subs/minimap?])
        open? @(subscribe [::subs/settings-modal-open?])]
    [:button#settings
     {:class (styles/settings-button minimap-open?)
      :on-mouse-over #(fire :on-ui-element? true)
      :on-mouse-out #(fire :on-ui-element? false)
      :on-click (if open?
                  #(dispatch [::events/close-settings-modal])
                  #(dispatch [::events/open-settings-modal]))}
     "Settings"]))

(defn- change-server-button []
  (let [minimap-open? @(subscribe [::subs/minimap?])
        open? @(subscribe [::subs/change-server-modal-open?])]
    [:button#main-menu
     {:class (styles/change-server-button minimap-open?)
      :on-mouse-over #(fire :on-ui-element? true)
      :on-mouse-out #(fire :on-ui-element? false)
      :on-click (if open?
                  #(dispatch [::events/close-change-server-modal])
                  #(dispatch [::events/open-change-server-modal]))}
     "Main Menu"]))

(defn- ping-counter []
  (let [fps? (:fps? @(subscribe [::subs/settings]))
        ping @(subscribe [::subs/ping])]
    [:button
     {:class (styles/ping-counter fps? ping)}
     (str "Ping: " (or ping "-"))]))

(defn- online-counter []
  (let [settings @(subscribe [::subs/settings])
        ping? (:ping? settings)
        fps? (:fps? settings)
        online @(subscribe [::subs/online])
        server @(subscribe [::subs/current-server])]
    [:button
     {:class (styles/online-counter ping? fps?)}
     (str "Online: " (or online "-") " (" server ")")]))

(defn- quest-progress []
  (let [{:keys [npc completed-kills required-kills] :as quest} @(subscribe [::subs/quest])]
    (when quest
      (let [completed-kills (if (> completed-kills required-kills)
                              required-kills
                              completed-kills)]
        [:div (styles/quest-progress)
         [:span "Kill " [:b required-kills] " " npc]
         [:span [:b (str completed-kills "/" required-kills)] " Killed"]]))))

(defn- change-server-modal []
  (r/create-class
    {:component-will-unmount #(fire :on-ui-element? false)
     :reagent-render
     (fn []
       [:div (styles/change-server-modal)
        [:p "Do you want to change " [:b "Character"] " or " [:b "Server"] "?"]
        [:div (styles/party-request-buttons-container)
         [:button
          {:class (styles/party-request-accept-button)
           :on-click #(dispatch [::events/re-init-game])}
          "Yes"]
         [:button
          {:class (styles/party-request-reject-button)
           :on-click #(dispatch [::events/close-change-server-modal])}
          "No"]]])}))

(defn- join-discord-modal []
  (r/create-class
    {:component-will-unmount #(fire :on-ui-element? false)
     :reagent-render
     (fn []
       [:div (styles/change-server-modal)
        [:p "Seems like you're enjoying the game! Would you like to join our " [:b "Discord Server"] "?"]
        [:div (styles/party-request-buttons-container)
         [:button
          {:class (styles/party-request-accept-button)
           :on-click #(do
                        (js/window.open "https://discord.gg/rmaTrYdV5V" "_blank")
                        (dispatch [::events/close-join-discord-server-modal]))}
          "Yes"]
         [:button
          {:class (styles/party-request-reject-button)
           :on-click #(dispatch [::events/close-join-discord-server-modal])}
          "No"]]])}))

(defn- settings-table [camera-rotation-speed edge-scroll-speed graphics-quality]
  [:table (styles/settings-table)
   [:thead
    [:tr
     [:td.center "Camera Rotation Speed"]
     [:td.no-padding
      [:div
       [:input
        {:type "range"
         :min "1"
         :max "30"
         :step "0.5"
         :value camera-rotation-speed
         :on-change #(dispatch-sync [::events/update-settings :camera-rotation-speed (-> % .-target .-value)])}]
       [:span (str camera-rotation-speed "/30")]]]]
    [:tr
     [:td.center "Edge Scrolling Speed"]
     [:td.no-padding
      [:div
       [:input {:type "range"
                :min "1"
                :max "200"
                :value edge-scroll-speed
                :on-change #(dispatch-sync [::events/update-settings :edge-scroll-speed (-> % .-target .-value)])}]
       [:span (str edge-scroll-speed "/200")]]]]

    [:tr
     [:td.center "Graphics Quality"]
     [:td.no-padding
      [:<>
       [:div
        [:input {:type "range"
                 :min "50"
                 :max "100"
                 :value graphics-quality
                 :on-change #(dispatch-sync [::events/update-settings :graphics-quality (-> % .-target .-value (/ 100))])}]
        [:span
         (str graphics-quality "/100")]]
       (when (> graphics-quality 75)
         [:span.small
          "(If you make it higher, you might encounter performance issues)"])]]]]])

(defn- controls-table []
  [:table (styles/settings-table)
   [:thead
    [:tr
     [:td.center "Forward"]
     [:td [:span.action-key.settings "W"]]]
    [:tr
     [:td.center "Backward"]
     [:td [:span.action-key.settings "S"]]]
    [:tr
     [:td.center "Left"]
     [:td [:span.action-key.settings "A"]]]
    [:tr
     [:td.center "Right"]
     [:td [:span.action-key.settings "D"]]]
    [:tr
     [:td.center "Select enemy"]
     [:td [:span.action-key.settings "Tab"]]]
    [:tr
     [:td.center "Run to selected enemy"]
     [:td [:span.action-key.settings "R"]]]
    [:tr
     [:td.center "Talk to NPC"]
     [:td [:span.action-key.settings "F"]]]
    [:tr
     [:td.center "Jump"]
     [:td [:span.action-key.settings "Space"]]]
    [:tr
     [:td.center "Chat"]
     [:td [:span.action-key.settings "Enter"]]]
    [:tr
     [:td.center "Character Panel"]
     [:td [:span.action-key.settings "C"]]]
    [:tr
     [:td.center "Leader Board"]
     [:td [:span.action-key.settings "L"]]]
    [:tr
     [:td.center "Rotate Camera"]
     [:td.no-padding [:span "Hold Right Click and Drag"]]]
    [:tr
     [:td.center "Double mouse right click"]
     [:td.no-padding [:span "Uses HP Potion"]]]]])

(defn- settings-modal []
  (r/create-class
    {:component-will-unmount #(fire :on-ui-element? false)
     :reagent-render
     (fn []
       (let [{:keys [sound?
                     fps?
                     ping?
                     minimap?
                     chase-camera?
                     camera-rotation-speed
                     edge-scroll-speed
                     graphics-quality]} @(subscribe [::subs/settings])]
         [:div
          {:class (styles/settings-modal)
           :on-mouse-over #(fire :on-ui-element? true)
           :on-mouse-out #(fire :on-ui-element? false)}
          [:div
           {:class (styles/settings-cancel)
            :on-click #(dispatch [::events/close-settings-modal])}]
          [:div (styles/settings-switches)
           [:div
            {:style {:display :flex
                     :flex-direction :column
                     :align-items :center}}
            [:strong "Sound"]
            [:label.switch
             [:input {:style {:outline :none}
                      :type "checkbox"
                      :checked sound?
                      :on-change #(dispatch-sync [::events/update-settings :sound? (not sound?)])}]
             [:span.slider.round]]]
           [:div
            {:style {:display :flex
                     :flex-direction :column
                     :align-items :center}}
            [:strong "FPS"]
            [:label.switch
             [:input {:style {:outline :none}
                      :type "checkbox"
                      :checked fps?
                      :on-change #(dispatch-sync [::events/update-settings :fps? (not fps?)])}]
             [:span.slider.round]]]

           [:div
            {:style {:display :flex
                     :flex-direction :column
                     :align-items :center}}
            [:strong "Ping"]
            [:label.switch
             [:input {:style {:outline :none}
                      :type "checkbox"
                      :checked ping?
                      :on-change #(dispatch-sync [::events/update-settings :ping? (not ping?)])}]
             [:span.slider.round]]]

           [:div
            {:style {:display :flex
                     :flex-direction :column
                     :align-items :center}}
            [:strong "Minimap"]
            [:label.switch
             [:input {:style {:outline :none}
                      :type "checkbox"
                      :checked minimap?
                      :on-change #(dispatch-sync [::events/update-settings :minimap? (not minimap?)])}]
             [:span.slider.round]]]
           [:div
            {:style {:display :flex
                     :flex-direction :column
                     :align-items :center}}
            [:strong "Chase Camera"]
            [:label.switch
             [:input {:style {:outline :none}
                      :type "checkbox"
                      :checked chase-camera?
                      :on-change #(dispatch-sync [::events/update-settings :chase-camera? (not chase-camera?)])}]
             [:span.slider.round]]]]
          [:br]
          [settings-table camera-rotation-speed edge-scroll-speed graphics-quality]
          [:br]
          [controls-table]
          [:button
           {:class (styles/settings-reset-tutorials-button)
            :on-click #(dispatch [::events/reset-tutorials])}
           "Reset tutorials"]

          [:hr (styles/init-modal-hr)]

          [:button
           {:class (styles/settings-exit-button)
            :on-click #(dispatch [::events/close-settings-modal])}
           "Exit"]]))}))

(defn- get-server-name-and-ping [server-name ping]
  (str server-name " (" (if ping (str ping "ms") "N/A") ")"))

(defn- server [server-name server-attrs _ _ _]
  (r/create-class
    {:component-did-mount #(dispatch [::events/fetch-server-stats server-name (:stats-url server-attrs)])
     :reagent-render
     (fn [server-name server-attrs username race class]
       (let [{:keys [ping
                     orcs
                     humans
                     number-of-players
                     max-number-of-same-race-players
                     max-number-of-players
                     ws-url]} server-attrs
             connecting-server @(subscribe [::subs/connecting-to-server])]
         [:tr
          [:td (styles/server-stats-orc-cell)
           (if orcs (str orcs "/" max-number-of-same-race-players) "~/~")]
          [:td (styles/server-stats-human-cell)
           (if humans (str humans "/" max-number-of-same-race-players) "~/~")]
          [:td (styles/server-stats-total-cell)
           (if number-of-players (str number-of-players "/" max-number-of-players) "~/~")]
          [:td (styles/server-name-cell)
           (get-server-name-and-ping server-name ping)]
          [:td
           [:button
            {:class [(styles/init-modal-connect-button) (when (= connecting-server server-name) "connecting")]
             :disabled (or (= number-of-players max-number-of-players)
                           connecting-server)
             :on-click #(dispatch [::events/connect-to-server
                                   server-name
                                   ws-url
                                   @username
                                   @race
                                   @class])}
            (if (= connecting-server server-name)
              "Connecting..."
              "Play")]]]))}))

(defn- server-list [username race class]
  (let [servers @(subscribe [::subs/servers])]
    [:div (styles/server-stats-container)
     (if (or (empty? servers) @(subscribe [::subs/initializing?]))
       [:span "Fetching servers list..."]
       [:table (styles/server-stats-table)
        [:thead
         [:tr
          [:th (styles/server-stats-orc-cell) "Orcs"]
          [:th (styles/server-stats-human-cell) "Humans"]
          [:th (styles/server-stats-total-cell) "Total"]
          [:th (styles/server-name-cell) "Server"]
          [:th]]]
        [:tbody
         (for [[k v] servers]
           ^{:key k}
           [server k v username race class])]])]))

(defn- username-input [username]
  [:input
   {:ref #(some-> % .focus)
    :value @username
    :on-change #(reset! username (-> % .-target .-value))
    :on-blur #(swap! username ui.utils/clean)
    :placeholder "ENTER USERNAME"
    :class (styles/init-modal-username-input)}])

(defn- select-race [race]
  [:<>
   [:p "Select race"]
   [:div (styles/init-modal-race-container)
    [:img
     {:style {:width "30%"
              :height :auto
              :position :absolute
              :left "35px"
              :top "55px"
              :cursor :pointer}
      :src "img/orcs.png"
      :on-click #(reset! race "orc")}]
    [:img
     {:style {:width "30%"
              :height :auto
              :position :absolute
              :right "35px"
              :top "55px"
              :cursor :pointer}
      :src "img/humans.png"
      :on-click #(reset! race "human")}]
    [:button
     {:class (styles/init-modal-orc-button (= "orc" @race))
      :on-click #(reset! race "orc")}
     "Orc"]
    [:button
     {:class (styles/init-modal-human-button (= "human" @race))
      :on-click #(reset! race "human")}
     "Human"]]])

(defn- select-class [class race]
  [:<>
   [:p "Select class"]
   [:div (styles/init-modal-class-container)
    [:button
     {:class (styles/init-modal-button @race (= "warrior" @class))
      :on-click #(reset! class "warrior")}
     "Warrior"]
    [:button
     {:class (styles/init-modal-button @race (= "priest" @class))
      :on-click #(reset! class "priest")}
     "Priest"]
    [:button
     {:class (styles/init-modal-button @race (= "mage" @class))
      :on-click #(reset! class "mage")}
     "Mage"]
    [:button
     {:class (styles/init-modal-button @race (= "asas" @class))
      :on-click #(reset! class "asas")}
     "Assassin"]]])

(defn- error-popup [err]
  [:div (styles/error-popup-modal)
   [:p (styles/error-popup-message) err]
   [:button
    {:class (styles/error-popup-ok-button)
     :on-click #(dispatch [::events/clear-init-modal-error])}
    "OK"]])

(defn- click-to-join-indicator-text []
  (let [err @(subscribe [::subs/init-modal-error])]
    (cond
      err
      [:span {:class (styles/click-to-join-indicator-text)}
       (str err " Please click to try again.")]

      @(subscribe [::subs/connecting-to-server])
      [:span {:class (styles/click-to-join-indicator-text)}
       "Connecting..."]

      (and (not dev?) (nil? @(subscribe [::subs/servers])))
      [:span {:class (styles/click-to-join-indicator-text)}
       "Fetching servers list..."]

      (not @(subscribe [::subs/available-servers]))
      [:span {:class (styles/click-to-join-indicator-text)}
       "Finding available servers..."]

      :else
      [:div (styles/click-to-join-indicator-text-container)
       [:span {:class [(styles/click-to-join-indicator-text) "bounce"]}
        "Click to Play"]
       [:span {:class [(styles/click-to-join-indicator-text) "or"]}
        "or"]
       [:button
        {:class (styles/select-your-character-button)
         :on-click (fn [e]
                     (.preventDefault e)
                     (.stopPropagation e)
                     (reset! game-init? true))}
        "Select your Character⚔️"]])))

(defn- click-to-join-modal []
  (r/create-class
    {:component-did-mount #(dispatch [::events/notify-ui-is-ready])
     :component-will-mount #(when-not dev? (dispatch [::events/fetch-server-list true]))
     :component-will-unmount #(fire :on-ui-element? false)
     :reagent-render
     (fn []
       [:div (styles/click-to-join-modal)
        [:a
         {:href "https://discord.gg/rmaTrYdV5V"
          :target "_blank"}
         [:img
          {:src "img/dc.png"
           :class (styles/discord)}]]
        [:div
         {:class (styles/click-to-join-modal-container)
          :on-click #(do
                       (dispatch [::events/clear-init-modal-error])
                       (dispatch [::events/connect-to-available-server]))}
         [click-to-join-indicator-text]]])}))

(def username (r/atom nil))
(def race (r/atom nil))
(def class (r/atom nil))

(on :set-player-for-init-modal
    (fn [{:keys [last-played-race last-played-class] :as player}]
      (when (seq player)
        (some->> (get-in player [(keyword last-played-class) :username]) (reset! username))
        (some->> last-played-race (reset! race))
        (some->> last-played-class (reset! class)))))

(defn- init-modal []
  (let []
    (r/create-class
      {:component-did-mount #(dispatch [::events/notify-ui-is-ready])
       :component-will-mount #(when-not dev? (dispatch [::events/fetch-server-list]))
       :component-will-unmount #(fire :on-ui-element? false)
       :reagent-render
       (fn []
         [:<>
          [:a
           {:href "https://discord.gg/rmaTrYdV5V"
            :target "_blank"}
           [:img
            {:src "img/dc.png"
             :class (styles/discord)}]]
          [:div
           {:class (styles/init-modal)
            :on-mouse-over #(fire :on-ui-element? true)
            :on-mouse-out #(fire :on-ui-element? false)}
           [username-input username]
           [select-race race]
           [select-class class race]
           [:hr (styles/init-modal-hr)]
           [server-list username race class]
           (when-let [err @(subscribe [::subs/init-modal-error])]
             [error-popup err])]])})))

(defn- mobile-user-modal []
  [:div {:class (styles/init-modal)}
   [:p "This game is not supported on mobile and tablet devices yet"]
   [:p "Please use a desktop computer to play"]])

(defn- on-mouse-down [e]
  (when (= (j/get e :button) 0)
    (if (> js/window.innerWidth 1440)
      (j/assoc-in! js/document [:body :style :cursor] "url(img/cursor_64_active.png) 23 23, auto")
      (j/assoc-in! js/document [:body :style :cursor] "url(img/cursor_48_active.png) 17 17, auto"))))

(defn- on-mouse-up [e]
  (when (= (j/get e :button) 0)
    (if (> js/window.innerWidth 1440)
      (j/assoc-in! js/document [:body :style :cursor] "url(img/cursor_64.png) 23 23, auto")
      (j/assoc-in! js/document [:body :style :cursor] "url(img/cursor_48.png) 17 17, auto"))))

(defn- connection-lost-modal []
  [:div (styles/connection-lost-modal)
   [:p "Connection lost! Please refresh the page."]
   [:button
    {:class (styles/connection-lost-button)
     :on-click #(js/window.location.reload)}
    "Refresh"]])

(defn- something-went-wrong-modal []
  [:div (styles/connection-lost-modal)
   [:p "Something went wrong. Please refresh the page."]
   [:button
    {:class (styles/connection-lost-button)
     :on-click #(js/window.location.reload)}
    "Refresh"]])

(defn- congrats-text []
  (when @(subscribe [::subs/congrats-text?])
    [:div
     {:style {:position :absolute
              :top "20%"
              :left "calc(50% + 25px)"
              :transform "translate(-50%, -50%)"
              :font-size "32px"
              :z-index 99
              :color :white}}
     [:span
      {:style {:text-shadow "-1px -1px 0 #000, 1px -1px 0 #000, -1px 1px 0 #000, 1px 1px 0 #000"}}
      "You completed your first quest and earned 25 Health and Mana potions! "]
     [:span
      "\uD83C\uDF89"]]))

(defn- global-message []
  (when-let [message @(subscribe [::subs/global-message])]
    [:div (styles/global-message)
     (if (map? message)
       [:div
        [:span (:text message)]
        (cond
          (:action-keys message) (into [:div.action-keys-wrapper]
                                       (map
                                         (fn [k]
                                           [:span.action-key.small k])
                                         (:action-keys message)))
          (:img message) [:div.image-container
                          [:img {:src (str "img/" (:img message))}]]
          :else [:span.action-key (:action-key message)])]
       [:span message])]))

(defn- add-mouse-listeners []
  (js/document.addEventListener "mousedown" on-mouse-down)
  (js/document.addEventListener "mouseup" on-mouse-up)
  (js/document.addEventListener "mousemove"
                                (fn [e]
                                  (reset! mouse-x (j/get e :x))
                                  (reset! mouse-y (j/get e :y))
                                  (reset! shop/mouse-x (j/get e :x))
                                  (reset! shop/mouse-y (j/get e :y)))))

(defn- add-key-listeners []
  (js/document.addEventListener "keydown"
                                (fn [e]
                                  (let [code (j/get e :code)]
                                    (cond
                                      (= code "Enter")
                                      (do
                                        (.preventDefault e)
                                        (dispatch [::events/send-message]))

                                      (= code "KeyL")
                                      (dispatch [::events/open-score-board])

                                      (= code "Space")
                                      (intro/next-step)

                                      (= code "Escape")
                                      (do
                                        (dispatch [::events/cancel-skill-move])
                                        (dispatch [::events/close-chat])
                                        (dispatch [::events/close-settings-modal])
                                        (dispatch [::events/close-change-server-modal]))))))
  (js/document.addEventListener "keyup"
                                (fn [e]
                                  (let [code (j/get e :code)]
                                    (cond
                                      (= code "KeyL")
                                      (dispatch [::events/close-score-board]))))))

(defn- left-panel []
  [:div (styles/left-panel @(subscribe [::subs/char-panel-open?]))
   (when @(subscribe [::subs/ping?])
     [ping-counter])
   [online-counter]
   [quest-progress]
   [chat]])

(defn main-panel []
  (r/create-class
    {:component-will-mount #(dispatch [::events/fetch-player-info])
     :component-did-mount
     (fn []
       (add-mouse-listeners)
       (add-key-listeners)
       (on :ui-init-game #(dispatch [::events/init-game %]))
       (on :ui-set-as-party-leader #(dispatch [::events/set-as-party-leader %]))
       (on :init-skills #(dispatch [::events/init-skills %]))
       (on :ui-selected-player #(dispatch [::events/set-selected-player %]))
       (on :ui-player-health #(dispatch [::events/set-health %]))
       (on :ui-player-mana #(dispatch [::events/set-mana %]))
       (on :ui-player-set-total-health-and-mana #(dispatch [::events/set-total-health-and-mana %]))
       (on :ui-cooldown #(dispatch-sync [::events/cooldown %]))
       (on :ui-cancel-skill #(dispatch-sync [::events/clear-cooldown %]))
       (on :ui-slow-down? #(dispatch-sync [::events/block-slow-down-skill %]))
       (on :ui-show-party-request-modal #(dispatch [::events/show-party-request-modal %]))
       (on :register-party-members #(dispatch [::events/register-party-members %]))
       (on :update-party-members #(dispatch [::events/update-party-members %]))
       (on :cancel-party #(dispatch [::events/cancel-party]))
       (on :add-global-message #(dispatch [::events/add-message-to-chat-all %]))
       (on :add-party-message #(dispatch [::events/add-message-to-chat-party %]))
       (on :ui-chat-error #(dispatch [::events/add-chat-error-msg %]))
       (on :show-re-spawn-modal #(dispatch [::events/show-re-spawn-modal %]))
       (on :ui-re-spawn #(dispatch [::events/re-spawn]))
       (on :close-party-request-modal #(dispatch [::events/close-party-request-modal]))
       (on :close-init-modal #(dispatch [::events/close-init-modal]))
       (on :ui-init-modal-error #(dispatch [::events/set-init-modal-error %]))
       (on :ui-set-score-board #(dispatch [::events/set-score-board %]))
       (on :ui-set-connection-lost #(dispatch [::events/set-connection-lost]))
       (on :ui-player-ready #(do
                               (reset! game-init? true)
                               (dispatch [::events/update-settings])
                               (dispatch [::events/show-tutorial-message])))
       (on :ui-show-tutorial-message #(dispatch [::events/show-tutorial-message]))
       (on :ui-update-ping #(dispatch [::events/update-ping %]))
       (on :ui-update-hp-potions #(dispatch [::events/update-hp-potions %]))
       (on :ui-update-mp-potions #(dispatch [::events/update-mp-potions %]))
       (on :ui-finish-tutorial-progress #(dispatch [::events/finish-tutorial-progress %]))
       (on :ui-show-congrats-text #(dispatch [::events/show-congrats-text]))
       (on :ui-show-adblock-warning-text #(dispatch [::events/show-global-message "You need to disable Adblock!" 3500]))
       (on :ui-ws-connected #(dispatch [::events/set-ws-connected]))
       (on :ui-show-panel? #(dispatch [::events/show-ui-panel? %]))
       (on :ui-got-defense-break #(dispatch [::events/got-defense-break %]))
       (on :ui-cured #(dispatch [::events/cured]))
       (on :ui-level-up #(dispatch [::events/level-up %]))
       (on :ui-set-exp #(dispatch [::events/set-exp %]))
       (on :ui-set-bp #(dispatch [::events/set-bp %]))
       (on :ui-toggle-char-panel #(dispatch [::events/toggle-char-panel]))
       (on :ui-show-global-message #(dispatch [::events/show-global-message %1 %2]))
       (on :ui-show-something-went-wrong? #(dispatch [::events/show-something-went-wrong? %]))
       (on :ui-set-total-coin #(dispatch [::events/set-total-coin %]))
       (on :ui-talk-to-npc #(dispatch [::events/talk-to-npc %]))
       (on :ui-update-quest-progress #(dispatch [::events/update-quest-progress %]))
       (on :ui-complete-quest #(dispatch [::events/complete-quest %]))
       (on :ui-ask-join-discord #(dispatch [::events/open-join-discord-server-modal]))
       (on :ui-update-inventory-and-coin #(dispatch [::events/update-inventory-and-coin %]))
       (on :ui-update-inventory-and-equip #(dispatch [::events/update-inventory-and-equip %]))
       (on :ui-open-shop #(dispatch [::events/open-shop %]))
       (on :ui-upgrade-item-result #(dispatch [::events/upgrade-item-result %])))
     :reagent-render
     (fn []
       [:div (styles/ui-panel)
        (cond
          device-dec/isMobile
          [mobile-user-modal]

          (not @game-init?)
          [click-to-join-modal]

          @(subscribe [::subs/init-modal-open?])
          [init-modal]

          :else
          (when @(subscribe [::subs/show-ui-panel?])
            [:<>
             [left-panel]
             [congrats-text]
             [global-message]
             (when @(subscribe [::subs/connection-lost?])
               [connection-lost-modal])
             (when @(subscribe [::subs/something-went-wrong?])
               [something-went-wrong-modal])
             [change-server-button]
             [settings-button]
             (when @(subscribe [::subs/settings-modal-open?])
               [settings-modal])
             (when @(subscribe [::subs/change-server-modal-open?])
               [change-server-modal])
             (when @(subscribe [::subs/join-discord-modal-open?])
               [join-discord-modal])
             [selected-player]
             (when @(subscribe [::subs/minimap?])
               [minimap])
             [party-list]
             [party-request-modal]
             [character-panel]
             (when @(subscribe [::subs/shop-panel-open?])
               [shop/shop-panel])
             (when @(subscribe [::subs/score-board-open?])
               [score-modal])
             (when-let [upgrade-item @(subscribe [::subs/upgrade-item])]
               [upgrade-item-modal upgrade-item])
             [re-spawn-modal]
             [actions-section]
             [temp-skill-img]
             [skill-description]]))])}))

(comment
  (dispatch [::events/show-global-message {:text "Adjust camera with Right Click"
                                           :img "right-click.png"}])

  (dispatch [::events/show-global-message {:text "Adjust camera with Right Click"
                                           :action-keys ["S" "A" "D"]}])

  (dispatch [::events/show-global-message "Congrats!"])
  )
