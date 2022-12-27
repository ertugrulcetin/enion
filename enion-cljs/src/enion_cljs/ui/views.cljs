(ns enion-cljs.ui.views
  (:require
    [applied-science.js-interop :as j]
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
  (str "http://localhost:8280/img/" img))

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
    "hpPotion" (img->img-url "break_defense.png")
    "mpPotion" (img->img-url "break_defense.png")))

(defn temp-skill-img []
  (when-let [skill-move @(subscribe [::subs/skill-move])]
    [:div
     {:style {:position :absolute
              :top @mouse-y
              :left @mouse-x
              :z-index 15
              :pointer-events :none}}
     [:img
      {:class (styles/skill-img)
       :src (skill->img (:skill skill-move))}]]))

(defn- skill [index skill]
  [:div {:class (styles/skill)
         :on-click #(dispatch [::events/update-skills-order index skill])}
   [:span (styles/skill-number) (inc index)]
   (when-not (= :none skill)
     [:img {:class (styles/skill-img)
            :src (skill->img skill)}])])

(defn- hp-bar []
  [:div (styles/hp-bar)
   [:div (styles/hp-hit)]
   [:div (styles/hp)]
   [:span (styles/hp-mp-text) "100/100"]])

(defn- mp-bar []
  [:div (styles/mp-bar)
   [:div (styles/mp-used)]
   [:div (styles/mp)]
   [:span (styles/hp-mp-text) "100/100"]])

(defn- hp-mp-bars []
  [:div (styles/hp-mp-container)
   [hp-bar]
   [mp-bar]])

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
    (:hit message) "hit"
    (:bp message) "bp"
    (:skill message) "skill"
    (:potion message) "using-potion"
    (:hp message) "hp-recover"
    (:mp message) "mp-recover"
    (:skill-failed message) "skill-failed"
    (:too-far message) "skill-failed"))

(defn- info-message->text [message]
  (cond
    (:damage message) (str "You took " (:damage message) " damage from " (:from message))
    (:hit message) (str (:to message) " received " (:hit message) " damage")
    (:bp message) (str "Earned " (:bp message) " battle points")
    (:skill message) (str "Using " (:skill message))
    (:skill-failed message) "Skill failed"
    (:too-far message) "Too far"
    (= (:potion message) :hp) "Using HP potion"
    (= (:potion message) :mp) "Using MP potion"
    (:hp message) "240 HP recovered"
    (:mp message) "120 MP recovered"))

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
  [:div (styles/selected-player)
   [:span (styles/selected-player-text)
    "0000000"]
   [:div (styles/hp-bar-selected-player)
    [:div (styles/hp-hit)]
    [:div (styles/hp)]]])

(defonce x (r/atom 0))
(defonce y (r/atom 0))

(comment
  (player/get-position)
  (js/clearInterval 35281)
  (js/setInterval
    (fn []
      (when-not (= "idle" (player/get-state))
        (let [pos (player/get-position)]
          (reset! x (+ (* (j/get pos :x) 5) 175))
          (reset! y (+ (* (j/get pos :z) 5) 175)))))
    250)
  )

;; Math formula of map
;; let scale = Map zoom size (#holder) / terrain size (100) ratio
;; let gap = (Map zoom size (#holder) - Minimap size) / 2
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
     :src "http://localhost:8280/img/minimap.png"}]])

(defn- minimap []
  (when @(subscribe [::subs/minimap-open?])
    [:div (styles/minimap)
     [:div (styles/map-overflow)
      [map-holder]
      [:div (styles/minimap-player)]]]))

;; TODO when game is ready then show HUD
(defn main-panel []
  (r/create-class
    {:component-did-mount
     (fn []
       (js/document.addEventListener "mousemove"
                                     (fn [e]
                                       (reset! mouse-x (j/get e :x))
                                       (reset! mouse-y (j/get e :y))))
       (js/document.addEventListener "keydown"
                                     (fn [e]
                                       (cond
                                         (= (j/get e :code) "Enter")
                                         (dispatch [::events/send-message])

                                         (= (j/get e :code) "KeyM")
                                         (dispatch [::events/toggle-minimap]))))
       (on :init-skills #(dispatch [::events/init-skills %])))
     :reagent-render
     (fn []
       [:div (styles/ui-panel)
        [selected-player]
        [minimap]
        [chat]
        [info-box]
        [actions-section]
        [temp-skill-img]])}))
