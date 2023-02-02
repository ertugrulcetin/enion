(ns enion-cljs.ui.subs
  (:require
    [common.enion.skills :as common.skills]
    [re-frame.core :refer [reg-sub]]))

(reg-sub
  ::box-open?
  (fn [db [_ type]]
    (-> db type :open?)))

(reg-sub
  ::info-box-messages
  (fn [db]
    (-> db :info-box :messages seq)))

(reg-sub
  ::any-info-box-messages?
  :<- [::info-box-messages]
  (fn [info-box-messages]
    (boolean info-box-messages)))

(reg-sub
  ::chat-messages
  (fn [db]
    (let [type (-> db :chat-box :type)]
      (-> db :chat-box :messages type seq))))

(reg-sub
  ::chat-input-active?
  (fn [db]
    (-> db :chat-box :active-input?)))

(reg-sub
  ::chat-type
  (fn [db]
    (-> db :chat-box :type)))

(reg-sub
  ::minimap-open?
  (fn [db]
    (:minimap-open? db)))

(reg-sub
  ::party-list-open?
  (fn [db]
    (:party-list-open? db)))

(reg-sub
  ::skills
  (fn [db]
    (-> db :player :skills)))

(reg-sub
  ::skill-move
  (fn [db]
    (-> db :player :skill-move)))

(reg-sub
  ::cooldown-in-progress?
  (fn [db [_ skill]]
    (-> db :player :cooldown (get skill) :in-progress?)))

(reg-sub
  ::blocked-skill?
  (fn [db [_ skill]]
    (let [hp-in-progress? (-> db :player :cooldown (get "hpPotion") :in-progress?)
          mp-in-progress? (-> db :player :cooldown (get "mpPotion") :in-progress?)]
      (cond
        (= "hpPotion" skill) mp-in-progress?
        (= "mpPotion" skill) hp-in-progress?
        (= "fleetFoot" skill) (-> db :player :slow-down-blocked?)
        :else false))))

(reg-sub
  ::not-enough-mana
  (fn [db [_ skill]]
    false))

(reg-sub
  ::selected-player
  (fn [db]
    (:selected-player db)))

(reg-sub
  ::health
  (fn [db]
    (-> db :player :health)))

(reg-sub
  ::total-health
  (fn [db]
    (-> db :player :total-health)))

(reg-sub
  ::mana
  (fn [db]
    (-> db :player :mana)))

(reg-sub
  ::total-mana
  (fn [db]
    (-> db :player :total-mana)))

(reg-sub
  ::skill-description
  (fn [db]
    (some->> (:skill-description db) (get common.skills/skills) :description)))
