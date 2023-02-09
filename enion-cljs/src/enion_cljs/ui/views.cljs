(ns enion-cljs.ui.views
  (:require
    [applied-science.js-interop :as j]
    [common.enion.skills :as common.skills]
    [enion-cljs.common :refer [fire on]]
    [enion-cljs.scene.entities.player :as player]
    [enion-cljs.ui.events :as events]
    [enion-cljs.ui.styles :as styles]
    [enion-cljs.ui.subs :as subs]
    [re-frame.core :refer [subscribe dispatch dispatch-sync]]
    [reagent.core :as r]))

(def mouse-x (r/atom nil))
(def mouse-y (r/atom nil))

(defn- img->img-url [img]
  (str "img/" img))

(defn skill->img [skill]
  (case skill
    "attackOneHand" (img->img-url "attack_one_hand.png")
    "attackSlowDown" (img->img-url "attack_slowdown.png")
    "shieldWall" (img->img-url "shield_wall.png")
    "fleetFoot" (img->img-url "fleet_foot.png")
    "attackDagger" (img->img-url "attack_dagger.png")
    "phantomVision" (img->img-url "phantom_vision.png")
    "hide" (img->img-url "hide.png")
    "attackRange" (img->img-url "attack_range.jpeg")
    "attackSingle" (img->img-url "attack_single.png")
    "teleport" (img->img-url "teleport.png")
    "heal" (img->img-url "heal.png")
    "cure" (img->img-url "cure.png")
    "breakDefense" (img->img-url "break_defense.png")
    "hpPotion" (img->img-url "hp.png")
    "mpPotion" (img->img-url "mp.png")))

(defn temp-skill-img []
  (when-let [skill-move @(subscribe [::subs/skill-move])]
    [:div
     {:style {:position :absolute
              :top @mouse-y
              :left @mouse-x
              :z-index 15
              :pointer-events :none}}
     [:img
      {:class (styles/skill-img false false)
       :src (skill->img (:skill skill-move))}]]))

(defn skill-description []
  (let [offset-width (r/atom 0)
        offset-height (r/atom 0)]
    (fn []
      (when-let [skill-description (and (nil? @(subscribe [::subs/skill-move]))
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
           :style {:background "black"
                   :color "#c2c2c2"
                   :max-width "350px"
                   :border "2px solid #10131dcc"
                   :border-radius "5px"
                   :padding "10px"}}
          [:span
           {:style {:font-size "14px"}}
           skill-description]]]))))

(defn- cooldown [skill]
  (let [cooldown-secs (-> skill common.skills/skills :cooldown)]
    (r/create-class
      {:component-did-mount
       (fn []
         (let [id (js/setTimeout
                    (fn []
                      (dispatch-sync [::events/set-cooldown-timeout-id nil skill]))
                    cooldown-secs)]
           (dispatch-sync [::events/set-cooldown-timeout-id id skill])))
       :reagent-render
       (fn []
         [:div (styles/cooldown (/ cooldown-secs 1000))])})))

(defn- skill [index skill]
  [:div {:class (styles/skill)
         :on-click #(dispatch [::events/update-skills-order index skill])
         :on-mouse-over #(dispatch [::events/show-skill-description skill])
         :on-mouse-out #(dispatch [::events/show-skill-description nil])}
   [:span (styles/skill-number) (inc index)]
   (when-not (= :none skill)
     [:div (styles/childs-overlayed)
      [:img {:class (styles/skill-img
                      (or @(subscribe [::subs/blocked-skill? skill]) @(subscribe [::subs/died?]))
                      @(subscribe [::subs/not-enough-mana? skill]))
             :src (skill->img skill)}]
      (when @(subscribe [::subs/cooldown-in-progress? skill])
        [cooldown skill])])])

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

(defn- party-member-hp-mp-bars [username health total-health]
  [:div (styles/party-member-hp-mp-container false)
   [party-member-hp-bar health total-health]
   [:span (styles/party-member-username) username]])

(defn- skill-bar []
  [:div (styles/skill-bar)
   (map-indexed
     (fn [i s]
       ^{:key (str s "-" i)}
       [skill i s])
     @(subscribe [::subs/skills]))])

(defn- actions-section []
  [:div (styles/actions-container)
   [hp-mp-bars]
   [skill-bar]])

;; TODO chat acikken karakter hareket edememeli
(defn- chat-message [msg]
  (let [party? (= :party @(subscribe [::subs/chat-type]))]
    [:div (when party? (styles/chat-part-message-box))
     [:strong (str (:from msg) ":")]
     [:span (:text msg)]
     [:br]]))

(defn- on-message-box-update [ref]
  (when-let [elem @ref]
    (let [gap (- (j/get elem :scrollHeight) (+ (j/get elem :scrollTop)
                                               (j/get elem :offsetHeight)))]
      (when (< gap 50)
        (j/assoc! elem :scrollTop (j/get elem :scrollHeight))))))

;; TODO add scroll
;; TODO when on hover disalbe character zoom in/out
(defn- chat-message-box []
  (let [ref (atom nil)]
    (r/create-class
      {:component-did-update #(on-message-box-update ref)
       :reagent-render (fn []
                         [:div
                          {:ref #(reset! ref %)
                           :class (styles/message-box)}
                          (for [[idx msg] (map-indexed vector @(subscribe [::subs/chat-messages]))]
                            ^{:key idx}
                            [chat-message msg])])})))

;; TODO add Player A has defeated Player B
(defn- chat []
  (let [open? @(subscribe [::subs/box-open? :chat-box])
        input-active? @(subscribe [::subs/chat-input-active?])
        chat-type @(subscribe [::subs/chat-type])
        ref (atom nil)]
    [:div {:class (styles/chat-wrapper)}
     [:button
      {:ref #(reset! ref %)
       :class (if open? (styles/chat-close-button) (styles/chat-open-button))
       :on-click (fn []
                   (dispatch [::events/toggle-box :chat-box])
                   (some-> @ref .blur))}
      (if open? "Close" "Open")]
     (when open?
       [:div (styles/chat)
        [chat-message-box]
        (when input-active?
          [:input
           {:ref #(some-> % .focus)
            :class (styles/chat-input)
            :on-change #(dispatch-sync [::events/set-chat-message (-> % .-target .-value)])
            :max-length 80}])
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

(comment
  {:damage 100
   :from "NeaTBuSTeR"}
  {:bp 62}
  {:hit 88
   :to "LeXXo"}
  {:skill "Smash raptor"}
  {:skill-failed true}
  {:too-far true}
  {:potion :hp}
  {:potion :mp}
  {:hp true}
  {:mp true})

(defn- info-message->class [message]
  (cond
    (:damage message) "damage"
    (:defense-break message) "damage"
    (:hit message) "hit"
    (:bp message) "bp"
    (:skill message) "skill"
    (:party-requested-user message) "skill"
    (:party-request-rejected message) "skill"
    (:heal message) "hp-recover"
    (:hp message) "hp-recover"
    (:cure message) "hp-recover"
    (:mp message) "mp-recover"
    (:skill-failed message) "skill-failed"
    (:too-far message) "skill-failed"
    (:not-enough-mana message) "skill-failed"
    (:party-request-failed message) "skill-failed"
    (:no-selected-player message) "skill-failed"))

(let [hp-message (-> "hpPotion" common.skills/skills :hp (str " HP recovered"))
      mp-message (-> "mpPotion" common.skills/skills :mp (str " MP recovered"))
      heal-message (-> "heal" common.skills/skills :hp (str " HP recovered"))]
  (defn- info-message->text [message]
    (cond
      (:damage message) (str "You took " (:damage message) " damage from " (:from message))
      (:hit message) (str (:to message) " received " (:hit message) " damage")
      (:bp message) (str "Earned " (:bp message) " battle points")
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
      (:party-request-rejected message) (str (:party-request-rejected message) " rejected your party request"))))

(defn- info-message [message]
  [:<>
   [:span {:class (info-message->class message)} (info-message->text message)]
   [:br]])

(defn- info-message-box []
  (let [ref (atom nil)]
    (r/create-class
      {:component-did-update #(on-message-box-update ref)
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
      [:div (styles/info-box-wrapper)
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

(def x (r/atom 0))
(def y (r/atom 0))

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
            :top (str (- @y) "px")}}
   [:img
    {:class (styles/minimap-img)
     :src "img/minimap.png"}]])

(defn- minimap []
  (let [interval-id (atom nil)]
    (r/create-class
      {:component-did-mount (fn []
                              (reset! interval-id (js/setInterval
                                                    (fn []
                                                      ;; TODO enable here
                                                      #_(when-not (= "idle" (player/get-state))
                                                          (let [pos (player/get-position)]
                                                            (reset! x (+ (* (j/get pos :x) 5) 175))
                                                            (reset! y (+ (* (j/get pos :z) 5) 175)))))
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

(defn countdown []
  (let [countdown-seconds (r/atom 10)
        interval-id (atom nil)]
    (r/create-class
      {:reagent-render (fn []
                         [:div (styles/party-request-count-down)
                          [:p (str "Seconds remaining: " @countdown-seconds)]])
       :component-did-mount (fn []
                              (reset! interval-id (js/setInterval
                                                    (fn []
                                                      (if (> @countdown-seconds 0)
                                                        (swap! countdown-seconds dec)
                                                        (dispatch [::events/close-part-request-modal])))
                                                    1000)))
       :component-will-unmount #(js/clearInterval @interval-id)})))

(comment
  (on-esc-pressed-for-1-5-seconds (fn []
                                    (println "heyy"))))

(defn party-request-modal []
  (let [{:keys [open? username on-accept on-reject]} @(subscribe [::subs/party-request-modal])]
    (when open?
      [:div (styles/party-request-modal)
       [:p (str "Do you want to join " username "'s party?")]
       [countdown]
       [:div (styles/party-request-buttons-container)
        [:button
         {:class (styles/party-request-accept-button)
          :on-click #(do
                       (on-accept)
                       (dispatch [::events/close-part-request-modal]))}
         "Accept"]
        [:button
         {:class (styles/party-request-reject-button)
          :on-click #(do
                       (on-reject)
                       (dispatch [::events/close-part-request-modal]))}
         "Reject"]]])))

(defn- party-list []
  (when @(subscribe [::subs/party-list-open?])
    [:div (styles/party-list-container @(subscribe [::subs/minimap-open?]))
     [:div (styles/party-action-button-container)
      [:button {:class (styles/party-action-button)
                :on-click #(fire :add-to-party)}
       "Add to party"]]
     [:div
      (for [{:keys [username health total-health]} @(subscribe [::subs/party-members])]
        ^{:key username}
        [party-member-hp-mp-bars username health total-health])]]))

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

;; TODO when game is ready then show HUD
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
                                                     (dispatch [::events/send-message])

                                                     (= code "KeyM")
                                                     (dispatch [::events/toggle-minimap])

                                                     (= code "KeyP")
                                                     (dispatch [::events/toggle-party-list])

                                                     (= code "Escape")
                                                     (dispatch [::events/cancel-skill-move])))))
       (on :init-skills #(dispatch [::events/init-skills %]))
       (on :ui-send-msg #(dispatch [::events/add-message-to-info-box %]))
       (on :ui-selected-player #(dispatch [::events/set-selected-player %]))
       (on :ui-player-health #(dispatch [::events/set-health %]))
       (on :ui-player-mana #(dispatch [::events/set-mana %]))
       (on :ui-player-set-total-health-and-mana #(dispatch [::events/set-total-health-and-mana %]))
       (on :ui-cooldown #(dispatch-sync [::events/cooldown %]))
       (on :ui-cancel-skill #(dispatch-sync [::events/cancel-skill %]))
       (on :ui-slow-down? #(dispatch-sync [::events/block-slow-down-skill %]))
       (on :ui-show-party-request-modal #(dispatch [::events/show-party-request-modal %]))
       (on :register-party-members #(dispatch [::events/register-party-members %]))
       (on :add-party-member #(dispatch [::events/add-party-member %]))
       (on :update-party-member-healths #(dispatch [::events/update-party-member-healths %])))
     :reagent-render
     (fn []
       [:div (styles/ui-panel)
        [selected-player]
        (when @(subscribe [::subs/minimap-open?])
          [minimap])
        [party-list]
        [chat]
        [party-request-modal]
        [info-box]
        [actions-section]
        [temp-skill-img]
        [skill-description]])}))
