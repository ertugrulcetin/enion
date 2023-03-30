(ns enion-cljs.ui.subs
  (:require
    [breaking-point.core :as bp]
    [common.enion.skills :as common.skills]
    [enion-cljs.ui.tutorial :as tutorials]
    [re-frame.core :refer [reg-sub]]))

(reg-sub
  ::current-player-id
  (fn [db]
    (-> db :player :id)))

(reg-sub
  ::box-open?
  (fn [db [_ type]]
    (-> db type :open?)))

(reg-sub
  ::settings
  (fn [db]
    (update (:settings db) :graphics-quality (fn [gq]
                                               (int (* gq 100))))))

(reg-sub
  ::settings-modal-open?
  (fn [db]
    (-> db :settings-modal :open?)))

(reg-sub
  ::change-server-modal-open?
  (fn [db]
    (-> db :change-server-modal :open?)))

(reg-sub
  ::ping?
  (fn [db]
    (-> db :settings :ping?)))

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
  ::chat-message
  (fn [db]
    (-> db :chat-box :message)))

(reg-sub
  ::minimap?
  (fn [db]
    (-> db :settings :minimap?)))

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
  ::hp-potions
  (fn [db]
    (-> db :player :hp-potions (or 0))))

(reg-sub
  ::mp-potions
  (fn [db]
    (-> db :player :mp-potions (or 0))))

(reg-sub
  ::race
  (fn [db]
    (-> db :player :race)))

(reg-sub
  ::class
  (fn [db]
    (-> db :player :class)))

(reg-sub
  ::blocked-skill?
  (fn [db [_ skill]]
    (let [hp-potions (-> db :player :hp-potions (or 0))
          mp-potions (-> db :player :mp-potions (or 0))
          hp-in-progress? (-> db :player :cooldown (get "hpPotion") :in-progress?)
          mp-in-progress? (-> db :player :cooldown (get "mpPotion") :in-progress?)]
      (cond
        (= "hpPotion" skill) (or mp-in-progress? (= hp-potions 0))
        (= "mpPotion" skill) (or hp-in-progress? (= mp-potions 0))
        (= "fleetFoot" skill) (-> db :player :slow-down-blocked?)
        :else false))))

(reg-sub
  ::not-enough-mana?
  (fn [db [_ skill]]
    (let [player-mana (-> db :player :mana)]
      (-> common.skills/skills (get skill) :required-mana (> player-mana)))))

(reg-sub
  ::selected-player
  (fn [db]
    (:selected-player db)))

(reg-sub
  ::health
  (fn [db]
    (-> db :player :health)))

(reg-sub
  ::died?
  :<- [::health]
  (fn [health]
    (= health 0)))

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
    (and (:show-skill-description? db) (some->> (:skill-description db) (get common.skills/skills)))))

(reg-sub
  ::party-members
  (fn [db]
    (->> db :party :members vals (sort-by :order))))

(reg-sub
  ::party-request-modal
  (fn [db]
    (:party-request-modal db)))

(reg-sub
  ::selected-party-member
  (fn [db]
    (-> db :party :selected-member)))

(reg-sub
  ::party-leader?
  (fn [db]
    (-> db :party :leader?)))

(reg-sub
  ::able-to-add-party-member?
  (fn [db]
    (and (or (-> db :party :members nil?)
             (-> db :party :leader?))
         (< (-> db :party :members count) 5))))

(reg-sub
  ::able-to-exit-from-party?
  (fn [db]
    (boolean (and (-> db :party :members seq)
                  (-> db :party :leader? not)))))

(reg-sub
  ::party-leader-selected-member?
  (fn [db]
    (boolean
      (and (-> db :party :leader?)
           (-> db :party :selected-member)))))

(reg-sub
  ::party-leader-selected-himself?
  (fn [db]
    (and (-> db :party :leader?)
         (-> db :party :selected-member (= (-> db :player :id))))))

(reg-sub
  ::re-spawn-modal
  (fn [db]
    (:re-spawn-modal db)))

(reg-sub
  ::init-modal-open?
  (fn [db]
    (-> db :init-modal :open?)))

(reg-sub
  ::init-modal-error
  (fn [db]
    (-> db :init-modal :error)))

(reg-sub
  ::init-modal-loading?
  (fn [db]
    (-> db :init-modal :loading?)))

(reg-sub
  ::ready-to-enter?
  (fn [db]
    (and (:ws-connected? db)
         (not (-> db :init-modal :loading?)))))

(reg-sub
  ::server-stats
  (fn [db]
    (:server-stats db)))

(reg-sub
  ::score-board-open?
  (fn [db]
    (-> db :score-board :open?)))

(reg-sub
  ::score-board
  (fn [db]
    (-> db :score-board :players)))

(reg-sub
  ::connection-lost?
  (fn [db]
    (:connection-lost? db)))

(reg-sub
  ::ping
  (fn [db]
    (:ping db)))

(reg-sub
  ::online
  (fn [db]
    (:online db)))

(reg-sub
  ::tutorials
  (fn [db [_ tutorial]]
    (if tutorial
      (-> db :tutorials (get tutorial))
      (let [finished-tutorials (set (keys (:tutorials db)))
            screen-height (-> db ::bp/breakpoints :screen-height)
            num-of-tutorials-to-show (if (< screen-height 500) 2 3)]
        (take num-of-tutorials-to-show (remove #(finished-tutorials (first %)) tutorials/tutorials-order))))))

(reg-sub
  ::congrats-text?
  (fn [db]
    (:congrats-text? db)))

(reg-sub
  ::adblock-warning-text?
  (fn [db]
    (:adblock-warning-text? db)))

(reg-sub
  ::show-hp-mp-potions-ads-button?
  (fn [db]
    (and (-> db :tutorials :what-is-the-first-quest?)
         (or (= 0 (-> db :player :hp-potions (or 0)))
             (= 0 (-> db :player :mp-potions (or 0)))))))

(reg-sub
  ::show-ui-panel?
  (fn [db]
    (:show-ui-panel? db)))

(reg-sub
  ::servers
  (fn [db]
    (-> db :servers :list)))

(reg-sub
  ::current-server
  (fn [db]
    (-> db :servers :current-server)))

(reg-sub
  ::connecting-to-server
  (fn [db]
    (-> db :servers :connecting)))

(reg-sub
  ::defense-break?
  (fn [db]
    (-> db :player :defense-break?)))

(reg-sub
  ::initializing?
  (fn [db]
    (:initializing? db)))

(reg-sub
  ::fullscreen?
  (fn [db]
    (:fullscreen? db)))
