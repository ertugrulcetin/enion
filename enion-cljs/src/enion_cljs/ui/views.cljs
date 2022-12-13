(ns enion-cljs.ui.views
  (:require
    [applied-science.js-interop :as j]
    [enion-cljs.ui.events :as events]
    [enion-cljs.ui.styles :as styles]
    [enion-cljs.ui.subs :as subs]
    [re-frame.core :refer [subscribe dispatch dispatch-sync]]
    [reagent.core :as r]))

(def mouse-x (r/atom nil))
(def mouse-y (r/atom nil))
(def skill-move (r/atom nil))

(def skill-slots (r/atom [:nova :teleport :mana :none :none :none :none :none]))

(defn skill->img [skill]
  (case skill
    :nova "http://localhost:8280/img/nova.jpeg"
    :teleport "https://hordes.io/assets/ui/skills/32.webp"
    :mana "https://hordes.io/assets/items/misc/misc1_q0.webp"))

(defn temp-skill-img []
  (when @skill-move
    [:div
     {:style {:position :absolute
              :top @mouse-y
              :left @mouse-x
              :z-index 15
              :pointer-events :none}}
     [:img
      {:class (styles/skill-img)
       :src (skill->img (:skill @skill-move))}]]))

(defn- skill [index skill]
  [:div {:class (styles/skill)
         :on-click (fn []
                     (if @skill-move
                       (let [skill-move-index (@skill-move :index)
                             new-skill (@skill-move :skill)
                             prev-skill (@skill-slots index)]
                         (do
                           (swap! skill-slots assoc index new-skill skill-move-index prev-skill)
                           (reset! skill-move nil)))
                       (when-not (= skill :none)
                         (reset! skill-move {:index index, :skill skill}))))}
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
     @skill-slots)])

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
        [:div (styles/message-box)
         (for [[idx msg] (map-indexed vector @(subscribe [::subs/chat-messages]))]
           ^{:key idx}
           [chat-message msg])]
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
      {:component-did-update (fn []
                               (when-let [elem @ref]
                                 (j/assoc! elem :scrollTop (j/get elem :scrollHeight))))
       :reagent-render (fn []
                         [:div {:ref #(reset! ref %)
                                :class (styles/info-message-box)}
                          (for [[idx message] (map-indexed vector
                                                           [{:damage 241 :from "NeaTBuSTeR"}
                                                            {:bp 62}
                                                            {:hit 88 :to "LeXXo"}
                                                            {:skill "Smash raptor"}
                                                            {:skill-failed true}
                                                            {:too-far true}
                                                            {:potion :hp}
                                                            {:potion :mp}
                                                            {:hp true}
                                                            {:mp true}]
                                                           #_@(subscribe [::subs/info-box-messages]))]
                            ^{:key idx}
                            [info-message message])])})))

(defn- info-box []
  (when true #_@(subscribe [::subs/info-box-messages])
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

(comment
  (def data [{:damage 241 :from "NeaTBuSTeR"}
             {:bp 62}
             {:hit 88 :to "LeXXo"}
             {:skill "Smash raptor"}
             {:skill-failed true}
             {:too-far true}
             {:potion :hp}
             {:potion :mp}
             {:hp true}
             {:mp true}])
  (def data1 (shuffle data))
  (def data2 (shuffle data))
  (def data3 (shuffle data))
  (def all-data [data1 data2 data3])
  (doseq [msg (first (shuffle all-data))]
    (dispatch-sync [::events/add-message-to-info-box msg]))
  (js/setInterval
    (fn []
      (doseq [msg (first (shuffle all-data))]
        (dispatch-sync [::events/add-message-to-info-box msg])))
    250)
  )

(defn- selected-player []
  [:div (styles/selected-player)
   [:span (styles/selected-player-text)
    "0000000"]
   [:div (styles/hp-bar-selected-player)
    [:div (styles/hp-hit)]
    [:div (styles/hp)]]])

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
                                       (when (= (j/get e :code) "Enter")
                                         (dispatch [::events/send-message]))
                                       (js/console.log e))))
     :reagent-render
     (fn []
       [:div (styles/ui-panel)
        [selected-player]
        [chat]
        [info-box]
        [actions-section]
        [temp-skill-img]])}))
