(ns enion-cljs.ui.views
  (:require
    [applied-science.js-interop :as j]
    [breaking-point.core :as bp]
    [enion-cljs.ui.styles :as styles]
    [enion-cljs.ui.subs :as subs]
    [re-frame.core :as re-frame]
    [reagent.core :as r]))

(comment
  (do
    (let [r (rand-int 100)
          s (str r "%")
          hp (js/document.getElementById "mp")
          hit (js/document.getElementById "mp-used")]
      (set! (.-style.width hp) s)
      (set! (.-style.width hit) s))

    (let [r (rand-int 100)
          s (str r "%")
          hp (js/document.getElementById "hp")
          hit (js/document.getElementById "hit")]
      (set! (.-style.width hp) s)
      (set! (.-style.width hit) s)))
  )

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

(defn- chat []
  [:div (styles/chat)
   [:div (styles/message-box)
    (for [[idx data] (map-indexed vector (range 10))]
      ^{:key idx}
      [:<>
       [:span
        [:b (str "Message" ": message-" data)]]
       [:br]])]
   [:div
    [:input
     {:ref #(some-> % .focus)
      :class (styles/chat-input)
      ;; :on-change #(dispatch [::events/set-chat-message (-> % .-target .-value)])
      :max-length 40}]]
   [:div
    [:button
     {:class (styles/chat-all)}
     "All"]
    [:button
     {:class (styles/chat-party)}
     "Party"]]])

(defn- info-box []
  [:div (styles/info-box)
   [:div
    [:button
     {:class (styles/collapse-button)}
     "Collapse"]]
   [:div (styles/info-message-box)
    (for [[idx data] (map-indexed vector (range 100))]
      ^{:key idx}
      [:<>
       [:span
        [:b (str "Message" ": message-" data)]]
       [:br]])]])

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
                                       (reset! mouse-y (j/get e :y)))))
     :reagent-render
     (fn []
       [:div (styles/ui-panel)
        [selected-player]
        [chat]
        [info-box]
        [actions-section]
        [temp-skill-img]])}))
