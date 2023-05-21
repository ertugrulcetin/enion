(ns enion-cljs.ui.shop
  (:require
    ["bad-words" :as bad-words]
    ["react-device-detect" :as device-dec]
    [applied-science.js-interop :as j]
    [clojure.string :as str]
    [common.enion.item :as item]
    [enion-cljs.common :refer [fire on dev? dlog]]
    [enion-cljs.ui.events :as events]
    [enion-cljs.ui.styles :as styles]
    [enion-cljs.ui.subs :as subs]
    [enion-cljs.ui.utils :as ui.utils :refer [img->img-url]]
    [re-frame.core :refer [subscribe dispatch dispatch-sync]]
    [reagent.core :as r]))

(def items-in-order
  [:maul
   :holy-square-edge
   :censure-axe
   :whispercut-dagger
   :nightmark-dagger
   :viperslice-dagger
   :bone-oracle-staff
   :twilight-crystal-staff
   :aetherwind-staff
   :ironclash-sword
   :steelstrike-sword
   :thunderlord-axe
   :woodguard-shield
   :ironwall-shield
   :titan-aegis-shield
   :journeyman-scroll
   :expert-scroll
   :grandmaster-scroll])

(def mouse-x (r/atom nil))
(def mouse-y (r/atom nil))

(defn item-description [_]
  (let [offset-width (r/atom 0)
        offset-height (r/atom 0)
        offset-top -100]
    (fn [{:keys [inventory? selected-inventory-item]}]
      (when (nil? selected-inventory-item)
        (when-let [{:keys [levels name buy sell class inc-chance]} (get item/items @(subscribe [::subs/item-description]))]
          (let [player-class @(subscribe [::subs/class])
                coin @(subscribe [::subs/coin])
                item-desc-type @(subscribe [::subs/item-description-type])
                offset-inventory (if inventory? 100 0)]
            [:div
             {:style {:position :absolute
                      :top (+ @mouse-y @offset-height offset-top)
                      :left (+ offset-inventory (- @mouse-x (/ @offset-width 2)))
                      :z-index 99
                      :pointer-events :none}}
             [:div
              {:ref #(do
                       (when-let [ow (j/get % :offsetWidth)]
                         (reset! offset-width ow))
                       (when-let [oh (j/get % :offsetHeight)]
                         (reset! offset-height oh)))
               :class (styles/item-description)}
              [:span.item-name (if levels (str name " (+1)") name)]
              (when class
                (let [same-class? (= player-class (clojure.core/name class))]
                  [:<>
                   [:br]
                   [:span {:class (if same-class? "same-class" "different-class")}
                    (str "Class: " (str/capitalize (clojure.core/name class)))]]))
              (when-let [ap (and levels (get-in levels [1 :ap]))]
                [:<>
                 [:br]
                 [:br]
                 [:span
                  (str "Attack Power: " ap)]])
              (when-let [defence (and levels (get-in levels [1 :defence]))]
                [:<>
                 [:br]
                 [:br]
                 [:span
                  (str "Defence: " defence "%")]])
              (when inc-chance
                [:<>
                 [:br]
                 [:br]
                 [:span
                  (str "Upgrades item, adding a " inc-chance "% increased chance of success")]])
              [:br]
              [:br]
              (case item-desc-type
                :buy [:span (when (< coin buy) {:class ["not-enough-coin"]})
                      (str "Buy: " (ui.utils/to-locale buy) " Coins")]
                :sell [:span (str "Sell: " (ui.utils/to-locale sell) " Coins")]
                nil)]]))))))

(defn- shop-items []
  (for [item items-in-order]
    ^{:key item}
    [:div {:class (styles/inventory-square)
           :on-mouse-enter #(dispatch [::events/show-item-description item :buy])
           :on-mouse-leave #(dispatch [::events/show-item-description nil])
           :on-click #(do
                        (dispatch [::events/show-item-description nil])
                        (dispatch [::events/show-buy-item-dialog item]))}
     [:img {:src (img->img-url (str "item/" (-> item item/items :img)))}]]))

(defn inventory-squares []
  (let [inventory @(subscribe [::subs/inventory])]
    (for [i (range item/inventory-size)
          :let [{:keys [item] :as item-attrs} (get inventory i)]]
      ^{:key i}
      [:div {:class (styles/inventory-square)
             :on-mouse-enter #(dispatch [::events/show-item-description item :sell])
             :on-mouse-leave #(dispatch [::events/show-item-description nil])
             :on-click #(do
                          (dispatch [::events/show-item-description nil])
                          (dispatch [::events/show-sell-item-dialog (assoc item-attrs :slot-id i)]))}
       (when item
         [:img {:src (img->img-url (str "item/" (-> item item/items :img)))}])])))

(defn buy-item-modal [selected-item]
  [:div (styles/buy-item-modal)
   [:p "Do you want to buy " [:b (-> item/items selected-item :name)] "?"]
   [:div (styles/party-request-buttons-container)
    [:button
     {:class (styles/buy-item-button-accept)
      :on-click #(dispatch [::events/buy-item selected-item])}
     "Yes"]
    [:button
     {:class (styles/buy-item-button-reject)
      :on-click #(dispatch [::events/cancel-buy-item])}
     "No"]]])

(defn sell-item-modal [selected-item-attrs]
  (println "selected-item-attrs:  " selected-item-attrs)
  [:div (styles/buy-item-modal)
   [:p "Do you want to sell " [:b (-> :item selected-item-attrs item/items :name)] "?"]
   [:div (styles/party-request-buttons-container)
    [:button
     {:class (styles/buy-item-button-accept)
      :on-click #(dispatch [::events/sell-item selected-item-attrs])}
     "Yes"]
    [:button
     {:class (styles/buy-item-button-reject)
      :on-click #(dispatch [::events/cancel-sell-item])}
     "No"]]])

(defn- error-popup [err]
  [:div (styles/error-popup-modal)
   [:div
    {:class (styles/settings-cancel)
     :on-click #(dispatch [::events/clear-buy-item-modal-error])}]
   [:p (styles/item-error-popup-message) err]
   [:button
    {:class (styles/item-error-popup-ok-button)
     :on-click #(dispatch [::events/clear-buy-item-modal-error])}
    "OK"]])

(defn shop-panel []
  [:<>
   [:div {:class (styles/shop-panel)
          :on-mouse-over #(fire :on-ui-element? true)
          :on-mouse-out #(fire :on-ui-element? false)}
    [:div
     {:class (styles/shop-close)}]
    [:div {:class (styles/shop-panel-container)}
     [:div {:class (styles/shop-wrapper)}
      [:div {:class (styles/item-container)}
       (shop-items)]]
     [:div {:class (styles/coins-header)}
      [:span "Coins: " @(subscribe [::subs/coin-str])]]
     [:div {:class (styles/shop-wrapper)}
      [:div {:class (styles/item-container)}
       (inventory-squares)]]]]
   [item-description]
   (when-let [selected-item @(subscribe [::subs/selected-buy-item])]
     [buy-item-modal selected-item])
   (when-let [selected-item @(subscribe [::subs/selected-sell-item])]
     [sell-item-modal selected-item])
   (when-let [err @(subscribe [::subs/buy-item-modal-error])]
     [error-popup err])])
