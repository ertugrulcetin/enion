(ns enion-cljs.ui.views
  (:require
    ["bad-words" :as bad-words]
    ["react-device-detect" :as device-dec]
    [applied-science.js-interop :as j]
    [common.enion.skills :as common.skills]
    [enion-cljs.common :refer [fire on dev?]]
    [enion-cljs.ui.events :as events]
    [enion-cljs.ui.styles :as styles]
    [enion-cljs.ui.subs :as subs]
    [enion-cljs.ui.tutorial :as tutorial]
    [enion-cljs.ui.utils :as ui.utils]
    [re-frame.core :refer [subscribe dispatch dispatch-sync]]
    [reagent.core :as r]))

(def mouse-x (r/atom nil))
(def mouse-y (r/atom nil))

(def how-often-to-show-ad 3)
(def death-count (atom 0))

(defn- img->img-url [img]
  (str "img/" img))

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
      (when-let [{:keys [name description cooldown required-mana]} (and (nil? @(subscribe [::subs/skill-move]))
                                                                        @(subscribe [::subs/skill-description]))]
        [:div
         {:style {:position :absolute
                  :top (- @mouse-y @offset-height 5)
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
            [:span.info "Required MP: " required-mana])]]))))

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
            change-skill-order-completed? @(subscribe [::subs/tutorials :how-to-change-skill-order?])]
        [:div {:id (str "skill-" skill)
               :ref (fn [ref]
                      (when ref
                        (when-let [[ref f] (get @event-listeners skill)]
                          (.removeEventListener ref "contextmenu" f false))
                        (let [f (fn [ev]
                                  (.preventDefault ev)
                                  (dispatch [::events/update-skills-order index skill])
                                  (when-not change-skill-order-completed?
                                    (fire :finish-tutorial-step :how-to-change-skill-order?))
                                  false)]
                          (.addEventListener ref "contextmenu" f false)
                          (swap! event-listeners assoc skill [ref f]))))
               :class (styles/skill)
               :on-click (fn []
                           (if skill-move
                             (dispatch [::events/update-skills-order index skill])
                             (let [event (js/KeyboardEvent. "keydown" #js {:code (str "Digit" (inc index))
                                                                           :key (inc index)
                                                                           :keyCode (+ 49 index)
                                                                           :bubbles true
                                                                           :cancelable true})
                                   event #js {:event event
                                              :key (+ 49 index)}]
                               (fire :process-skills-from-skill-bar-clicks event))))
               :on-mouse-over #(dispatch [::events/show-skill-description skill])
               :on-mouse-out #(dispatch [::events/show-skill-description nil])}
         [:span (styles/skill-number) (inc index)]
         (when (= "hpPotion" skill)
           [:span (styles/potion-count) hp-potions])
         (when (= "mpPotion" skill)
           [:span (styles/potion-count) mp-potions])
         (when-not (= :none skill)
           [:div (styles/childs-overlayed)
            [:img {:class (styles/skill-img
                            (or @(subscribe [::subs/blocked-skill? skill]) @(subscribe [::subs/died?]))
                            @(subscribe [::subs/not-enough-mana? skill]))
                   :src (skill->img skill)}]
            (when @(subscribe [::subs/cooldown-in-progress? skill])
              [cooldown skill])])]))))

(defn- hp-bar []
  (let [health @(subscribe [::subs/health])
        total-health @(subscribe [::subs/total-health])
        health-perc (/ (* 100 health) total-health)]
    [:div (styles/hp-bar)
     [:div (styles/hp-hit health-perc)]
     [:div (styles/hp health-perc)]
     [:span (styles/hp-mp-text) (str health "/" total-health)]]))

(defn- mp-bar []
  (let [mana @(subscribe [::subs/mana])
        total-mana @(subscribe [::subs/total-mana])
        mana-perc (/ (* 100 mana) total-mana)]
    [:div (styles/mp-bar)
     [:div (styles/mp-used mana-perc)]
     [:div (styles/mp mana-perc)]
     [:span (styles/hp-mp-text) (str mana "/" total-mana)]]))

(defn- hp-mp-bars []
  [:div (styles/hp-mp-container)
   [hp-bar]
   [mp-bar]])

(defn- party-member-hp-bar [health total-health]
  (let [health-perc (/ (* 100 health) total-health)]
    [:div (styles/party-member-hp-bar)
     [:div (styles/party-member-hp-hit health-perc)]
     [:div (styles/party-member-hp health-perc)]]))

(defn- party-member-hp-mp-bars [id username health total-health]
  [:div
   {:class (styles/party-member-hp-mp-container (= id @(subscribe [::subs/selected-party-member])))
    :on-click #(do
                 (fire :select-party-member id)
                 (dispatch [::events/select-party-member id]))}
   [party-member-hp-bar health total-health]
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
   [hp-mp-bars]
   [skill-bar]])

(defn- chat-message [msg]
  (let [party? (= :party @(subscribe [::subs/chat-type]))
        killer (:killer msg)
        killer-race (:killer-race msg)
        killed (:killed msg)]
    (if killer
      [:div
       [:strong
        {:class (if (= "orc" killer-race) "orc-defeats" "human-defeats")}
        (str killer " defeated " killed "")]
       [:br]]
      [:div (when party? (styles/chat-part-message-box))
       (if (= "System" (:from msg))
         [:strong (:text msg)]
         [:<>
          [:strong (str (:from msg) ":")]
          [:span (:text msg)]])
       [:br]])))

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

(defn- chat-message-box []
  (let [ref (atom nil)]
    (r/create-class
      {:component-did-mount (fn [] (some-> @ref scroll-to-bottom))
       :component-did-update #(on-message-box-update ref)
       :reagent-render (fn []
                         [:div
                          {:ref #(some->> % (reset! ref))
                           :class (styles/message-box)}
                          (for [[idx msg] (map-indexed vector @(subscribe [::subs/chat-messages]))]
                            ^{:key idx}
                            [chat-message msg])])})))

;; had to duplicate (chat-message-box) this because of the scroll to bottom - refactor at some point
(defn- party-chat-message-box []
  (let [ref (atom nil)]
    (r/create-class
      {:component-did-mount (fn [] (some-> @ref scroll-to-bottom))
       :component-did-update #(on-message-box-update ref)
       :reagent-render (fn []
                         [:div
                          {:ref #(some->> % (reset! ref))
                           :class (styles/message-box)}
                          (for [[idx msg] (map-indexed vector @(subscribe [::subs/chat-messages]))]
                            ^{:key idx}
                            [chat-message msg])])})))

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
           :placeholder "Press ENTER to enable chat..."
           :class (styles/chat-input)
           :on-change #(dispatch-sync [::events/set-chat-message (-> % .-target .-value)])
           :max-length 60}])})))

(defn- chat []
  (let [open? @(subscribe [::subs/box-open? :chat-box])
        input-active? @(subscribe [::subs/chat-input-active?])
        chat-type @(subscribe [::subs/chat-type])]
    [:div#chat-wrapper
     {:class (styles/chat-wrapper)
      :on-mouse-over #(fire :on-ui-element? true)
      :on-mouse-out #(fire :on-ui-element? false)}
     [:button
      {:class (if open? (styles/chat-close-button) (styles/chat-open-button))
       :on-click #(dispatch [::events/toggle-box :chat-box])}
      (if open? "Close" "Open")]
     (when open?
       [:div (styles/chat)
        (if (= chat-type :all)
          [chat-message-box]
          [party-chat-message-box])
        [chat-input input-active?]
        [:div
         [:button
          {:class [(styles/chat-all-button) (when (= chat-type :all)
                                              (styles/chat-all-button-selected))]
           :on-click #(dispatch [::events/set-chat-type :all])}
          "All"]
         [:button
          {:class [(styles/chat-party-button) (when (= chat-type :party)
                                                (styles/chat-party-button-selected))]
           :on-click #(dispatch [::events/set-chat-type :party])}
          "Party"]]])]))

(defn- info-message->class [message]
  (cond
    (:damage message) "damage"
    (:defense-break message) "damage"
    (:hit message) "hit"
    (:bp message) "bp"
    (:skill message) "skill"
    (:heal message) "hp-recover"
    (:hp message) "hp-recover"
    (:cure message) "hp-recover"
    (:mp message) "mp-recover"
    (:skill-failed message) "skill-failed"
    (:ping-high message) "skill-failed"
    (:too-far message) "skill-failed"
    (:not-enough-mana message) "skill-failed"
    (:party-request-failed message) "skill-failed"
    (:no-selected-player message) "skill-failed"
    (:re-spawn-error message) "skill-failed"
    :else "skill"))

(let [hp-message (-> "hpPotion" common.skills/skills :hp (str " HP recovered"))
      mp-message (-> "mpPotion" common.skills/skills :mp (str " MP recovered"))
      heal-message (-> "heal" common.skills/skills :hp (str " HP recovered"))]
  (defn- info-message->text [message]
    (cond
      (:ping-high message) "Ping too high! Can't process your request"
      (:damage message) (str "You took " (:damage message) " damage from " (:from message))
      (:hit message) (str (:to message) " received " (:hit message) " damage")
      (:skill message) (str "Using " (:skill message))
      (:skill-failed message) "Skill failed"
      (:too-far message) "Too far"
      (:heal message) heal-message
      (:hp message) hp-message
      (:mp message) mp-message
      (:cure message) "Toxic effect removed"
      (:defense-break message) "Infected with Toxic Spores"
      (:not-enough-mana message) "Not enough mana!"
      (:party-request-failed message) "Processing party request failed"
      (:no-selected-player message) "No player selected"
      (:party-requested-user message) (str "You invited " (:party-requested-user message) " to join your party")
      (:party-request-rejected message) (str (:party-request-rejected message) " rejected your party request")
      (:joined-party message) (str (:joined-party message) " joined the party")
      (:removed-from-party message) "You've been removed from the party"
      (:member-removed-from-party message) (str (:member-removed-from-party message) " has been removed from the party")
      (:party-cancelled message) "The party has been cancelled"
      (:member-exit-from-party message) (str (:member-exit-from-party message) " exit from the party")
      (:bp message) (str "Earned " (:bp message) " Battle Points (BP)")
      (:re-spawn-error message) (:re-spawn-error message))))

(defn- info-message [message]
  [:<>
   [:span {:class (info-message->class message)} (info-message->text message)]
   [:br]])

(defn- info-message-box []
  (let [ref (atom nil)]
    (r/create-class
      {:component-did-update #(on-message-box-update ref true)
       :reagent-render (fn []
                         [:div
                          {:ref #(reset! ref %)
                           :class (styles/info-message-box)}
                          (for [[idx message] (map-indexed vector @(subscribe [::subs/info-box-messages]))]
                            ^{:key idx}
                            [info-message message])])})))

(defn- info-box []
  (when @(subscribe [::subs/any-info-box-messages?])
    (let [open? @(subscribe [::subs/box-open? :info-box])
          ref (atom nil)]
      [:div
       {:class (styles/info-box-wrapper)
        :on-mouse-over #(fire :on-ui-element? true)
        :on-mouse-out #(fire :on-ui-element? false)}
       [:button
        {:ref #(reset! ref %)
         :class (if open? (styles/info-close-button) (styles/info-open-button))
         :on-click (fn []
                     (dispatch [::events/toggle-box :info-box])
                     (some-> @ref .blur))}
        (if open? "Close" "Open")]
       (when open?
         [:div (styles/info-box)
          [info-message-box]])])))

(defn- selected-player []
  (when-let [{:keys [username health enemy?]} @(subscribe [::subs/selected-player])]
    [:div (styles/selected-player)
     [:span (styles/selected-player-text enemy?)
      username]
     [:div (styles/hp-bar-selected-player)
      [:div (styles/hp-hit health)]
      [:div (styles/hp health)]]]))

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
                            [:p "Press OK to return to the respawn point at the base"])
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
   [:td (styles/score-modal-orc-color) (display-class (:class orc))]
   [:td (styles/score-modal-orc-color) (:bp orc)]])

(defn- human-row [human]
  [:<>
   [:td (styles/score-modal-human-color) (:username human)]
   [:td (styles/score-modal-human-color) (display-class (:class human))]
   [:td (styles/score-modal-human-color) (:bp human)]])

(defn- orc-human-row [orc human]
  [:tr
   (if orc [orc-row orc] [:<> [:td] [:td] [:td]])
   (if human [human-row human] [:<> [:td] [:td] [:td]])])

(defn- score-modal []
  (r/create-class
    {:component-will-unmount #(fire :on-ui-element? false)
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
             {:colSpan "3"
              :style {:color styles/orc-color}}
             "Orcs"]
            [:th {:colSpan "3"
                  :style {:color styles/human-color}} "Humans"]]
           [:tr
            [:th (styles/score-modal-orc-color) "Player"]
            [:th (styles/score-modal-orc-color) "Class"]
            [:th (styles/score-modal-orc-color) "Battle Point"]
            [:th (styles/score-modal-human-color) "Player"]
            [:th (styles/score-modal-human-color) "Class"]
            [:th (styles/score-modal-human-color) "Battle Point"]]]
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
   [:div (styles/party-action-button-container)
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
        [:button#add-to-party {:class (styles/party-action-button)
                               :on-click #(fire :add-to-party)}
         "Add to party"]

        @(subscribe [::subs/able-to-exit-from-party?])
        [:button {:class (styles/party-action-button)
                  :on-click #(fire :exit-from-party)}
         "Exit from party"]))]
   (for [{:keys [id username health total-health]} @(subscribe [::subs/party-members])]
     ^{:key id}
     [party-member-hp-mp-bars id username health total-health])])

;; TODO add general component that prevents space press to enable button
(defn- settings-button []
  (let [minimap-open? @(subscribe [::subs/minimap?])
        open? @(subscribe [::subs/settings-modal-open?])]
    [:button#settings-button
     {:class (styles/settings-button minimap-open?)
      :on-click (if open?
                  #(dispatch [::events/close-settings-modal])
                  #(dispatch [::events/open-settings-modal]))}
     "Settings"]))

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

(defn- tutorials []
  (let [settings @(subscribe [::subs/settings])
        ping? (:ping? settings)
        fps? (:fps? settings)]
    [:div (styles/tutorial-container (and ping? fps?))
     [:div {:style {:display :flex
                    :flex-direction :column
                    :gap "5px"}}
      (for [[t title f show-ui-panel?] @(subscribe [::subs/tutorials])]
        ^{:key t}
        [:button
         {:class (styles/tutorials)
          :on-click #(tutorial/start-intro (f) nil show-ui-panel?)}
         title])]]))

(defn- temp-container-for-fps-ping-online []
  [:div#temp-container-for-fps-ping-online
   (styles/temp-container-for-fps-ping-online)])

(defn- settings-modal []
  (r/create-class
    {:component-will-unmount #(fire :on-ui-element? false)
     :reagent-render
     (fn []
       (let [{:keys [sound?
                     fps?
                     ping?
                     minimap?
                     camera-rotation-speed
                     edge-scroll-speed
                     graphics-quality]} @(subscribe [::subs/settings])]
         [:div
          {:class (styles/settings-modal)
           :on-mouse-over #(fire :on-ui-element? true)
           :on-mouse-out #(fire :on-ui-element? false)}
          [:div
           {:style {:display :flex
                    :flex-direction :row
                    :justify-content :center
                    :gap "30px"}}
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
             [:span.slider.round]]]]

          [:hr (styles/init-modal-hr)]

          [:div
           {:style {:display :flex
                    :flex-direction :column
                    :margin-top "25px"}}
           [:strong "Camera Rotation Speed"]
           [:span {:style {:font-size "15px"}}
            "(Allows to rotate the camera by right-clicking and dragging the mouse)"]
           [:input
            {:style {:outline :none}
             :type "range"
             :min "1"
             :max "30"
             :step "0.5"
             :value camera-rotation-speed
             :on-change #(dispatch-sync [::events/update-settings :camera-rotation-speed (-> % .-target .-value)])}]
           [:span {:style {:font-size "25px"}} (str camera-rotation-speed "/30")]]

          [:hr (styles/init-modal-hr)]

          [:div
           {:style {:display :flex
                    :flex-direction :column
                    :margin-top "25px"}}
           [:strong "Edge Scrolling Speed"]
           [:span {:style {:font-size "15px"}}
            "(Allows to move the camera by moving the mouse to the edges of the screen - You can press Key Q or E)"]
           [:input {:style {:outline :none}
                    :type "range"
                    :min "1"
                    :max "200"
                    :value edge-scroll-speed
                    :on-change #(dispatch-sync [::events/update-settings :edge-scroll-speed (-> % .-target .-value)])}]
           [:span {:style {:font-size "25px"}} (str edge-scroll-speed "/200")]]

          [:hr (styles/init-modal-hr)]

          [:div
           {:style {:display :flex
                    :flex-direction :column
                    :margin-top "25px"}}
           [:strong "Graphics Quality"]
           (when (> graphics-quality 75)
             [:span {:style {:font-size "15px"}}
              "(If you make it higher, you might encounter performance issues)"])
           [:input {:style {:outline :none}
                    :type "range"
                    :min "50"
                    :max "100"
                    :value graphics-quality
                    :on-change #(dispatch-sync [::events/update-settings :graphics-quality (-> % .-target .-value (/ 100))])}]
           [:span {:style {:font-size "25px"}} (str graphics-quality "/100")]]

          [:hr (styles/init-modal-hr)]

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
     (if (empty? servers)
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
    :placeholder "Enter username, leave empty for random one"
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

(defn- init-modal []
  (let [username (r/atom nil)
        race (r/atom nil)
        class (r/atom nil)]
    (r/create-class
      {:component-did-mount #(dispatch [::events/notify-ui-is-ready])
       :component-will-mount #(when-not dev? (dispatch [::events/fetch-server-list]))
       :component-will-unmount #(fire :on-ui-element? false)
       :reagent-render
       (fn []
         [:<>
          [:a
           {:href "https://discord.gg/xV4Q2ncz"
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
      "You completed your first quest and earned 30 Health and Mana potions! "]
     [:span
      "\uD83C\uDF89"]]))

(defn- adblock-warning-text []
  (when @(subscribe [::subs/adblock-warning-text?])
    [:div
     {:style {:position :absolute
              :background-color :black
              :border-radius "5px"
              :padding "10px"
              :top "20%"
              :left "calc(50%)"
              :transform "translate(-50%, -50%)"
              :font-size "32px"
              :z-index 99
              :color :white}}
     [:span "You need to disable Adblock!"]]))

(defn main-panel []
  (r/create-class
    {:component-did-mount
     (fn []
       (js/document.addEventListener "mousedown" on-mouse-down)
       (js/document.addEventListener "mouseup" on-mouse-up)
       (js/document.addEventListener "mousemove"
                                     (fn [e]
                                       (reset! mouse-x (j/get e :x))
                                       (reset! mouse-y (j/get e :y))))
       (js/document.addEventListener "keydown" (fn [e]
                                                 (let [code (j/get e :code)]
                                                   (cond
                                                     (= code "Enter")
                                                     (do
                                                       (.preventDefault e)
                                                       (dispatch [::events/send-message]))

                                                     (= code "Tab")
                                                     (do
                                                       (.preventDefault e)
                                                       (dispatch [::events/open-score-board]))

                                                     (= code "Space")
                                                     (tutorial/next-intro)

                                                     (= code "Escape")
                                                     (do
                                                       (dispatch [::events/cancel-skill-move])
                                                       (dispatch [::events/close-chat])
                                                       (dispatch [::events/close-settings-modal]))))))
       (js/document.addEventListener "keyup" (fn [e]
                                               (let [code (j/get e :code)]
                                                 (cond
                                                   (= code "Tab")
                                                   (do
                                                     (.preventDefault e)
                                                     (dispatch [::events/close-score-board]))))))
       (on :ui-init-game #(dispatch [::events/init-game %]))
       (on :ui-set-as-party-leader #(dispatch [::events/set-as-party-leader %]))
       (on :init-skills #(dispatch [::events/init-skills %]))
       (on :ui-send-msg #(dispatch [::events/add-message-to-info-box %]))
       (on :ui-selected-player #(dispatch [::events/set-selected-player %]))
       (on :ui-player-health #(dispatch [::events/set-health %]))
       (on :ui-player-mana #(dispatch [::events/set-mana %]))
       (on :ui-player-set-total-health-and-mana #(dispatch [::events/set-total-health-and-mana %]))
       (on :ui-cooldown #(dispatch-sync [::events/cooldown %]))
       (on :ui-cancel-skill #(dispatch-sync [::events/clear-cooldown %]))
       (on :ui-slow-down? #(dispatch-sync [::events/block-slow-down-skill %]))
       (on :ui-show-party-request-modal #(dispatch [::events/show-party-request-modal %]))
       (on :register-party-members #(dispatch [::events/register-party-members %]))
       (on :update-party-member-healths #(dispatch [::events/update-party-member-healths %]))
       (on :cancel-party #(dispatch [::events/cancel-party]))
       (on :add-global-message #(dispatch [::events/add-message-to-chat-all %]))
       (on :add-party-message #(dispatch [::events/add-message-to-chat-party %]))
       (on :ui-chat-error #(dispatch [::events/add-chat-error-msg %]))
       (on :show-re-spawn-modal #(dispatch [::events/show-re-spawn-modal %]))
       (on :close-re-spawn-modal #(dispatch [::events/close-re-spawn-modal %]))
       (on :close-party-request-modal #(dispatch [::events/close-party-request-modal]))
       (on :clear-all-cooldowns #(dispatch [::events/clear-all-cooldowns]))
       (on :close-init-modal #(dispatch [::events/close-init-modal]))
       (on :ui-init-modal-error #(dispatch [::events/set-init-modal-error %]))
       (on :ui-set-server-stats #(dispatch [::events/set-server-stats %]))
       (on :ui-set-score-board #(dispatch [::events/set-score-board %]))
       (on :ui-set-connection-lost #(dispatch [::events/set-connection-lost]))
       (on :ui-player-ready #(dispatch [::events/update-settings]))
       (on :ui-update-ping #(dispatch [::events/update-ping %]))
       (on :ui-update-hp-potions #(dispatch [::events/update-hp-potions %]))
       (on :ui-update-mp-potions #(dispatch [::events/update-mp-potions %]))
       (on :ui-finish-tutorial-progress #(dispatch [::events/finish-tutorial-progress %]))
       (on :ui-show-congrats-text #(dispatch [::events/show-congrats-text]))
       (on :ui-show-adblock-warning-text #(dispatch [::events/show-adblock-warning-text]))
       (on :ui-ws-connected #(dispatch [::events/set-ws-connected]))
       (on :ui-show-panel? #(dispatch [::events/show-ui-panel? %])))
     :reagent-render
     (fn []
       [:div (styles/ui-panel)
        (cond
          device-dec/isMobile
          [mobile-user-modal]

          @(subscribe [::subs/init-modal-open?])
          [init-modal]

          :else
          (when @(subscribe [::subs/show-ui-panel?])
            [:<>
             [congrats-text]
             [adblock-warning-text]
             [temp-container-for-fps-ping-online]
             (when @(subscribe [::subs/connection-lost?])
               [connection-lost-modal])
             (when @(subscribe [::subs/ping?])
               [ping-counter])
             [online-counter]
             [tutorials]
             [settings-button]
             (when @(subscribe [::subs/settings-modal-open?])
               [settings-modal])
             [selected-player]
             (when @(subscribe [::subs/minimap?])
               [minimap])
             [party-list]
             [chat]
             [party-request-modal]
             [info-box]
             (when @(subscribe [::subs/score-board-open?])
               [score-modal])
             [re-spawn-modal]
             [actions-section]
             [temp-skill-img]
             [skill-description]]))])}))
