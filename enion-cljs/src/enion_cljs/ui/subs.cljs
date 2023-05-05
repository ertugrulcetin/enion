(ns enion-cljs.ui.subs
  (:require
    [breaking-point.core :as bp]
    [clojure.string :as str]
    [common.enion.skills :as common.skills]
    [enion-cljs.ui.tutorial :as tutorials]
    [re-frame.core :refer [reg-sub reg-sub-raw]]
    [reagent.ratom :as ratom]))

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
  ::level
  (fn [db]
    (-> db :player :level)))

(reg-sub
  ::attack-power
  (fn [db]
    (-> db :player :attack-power)))

(reg-sub
  ::exp
  (fn [db]
    (-> db :player :exp)))

(reg-sub
  ::coin
  (fn [db]
    (-> db :player :coin)))

(reg-sub
  ::bp
  (fn [db]
    (or (-> db :player :bp) 0)))

(reg-sub
  ::required-exp
  (fn [db]
    (-> db :player :required-exp)))

(reg-sub
  ::race
  (fn [db]
    (-> db :player :race)))

(reg-sub
  ::class
  (fn [db]
    (-> db :player :class)))

(reg-sub
  ::username
  (fn [db]
    (-> db :player :username)))

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
  ::not-enough-mana-or-level?
  (fn [db [_ skill]]
    (let [player-mana (-> db :player :mana)
          level (-> db :player :level)]
      (or (-> common.skills/skills (get skill) :required-mana (> player-mana))
          (-> common.skills/skills (get skill) :required-level (> level))))))

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
  ::something-went-wrong?
  (fn [db]
    (:something-went-wrong? db)))

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
  (fn [db]))

(reg-sub
  ::congrats-text?
  (fn [db]
    (:congrats-text? db)))

(reg-sub
  ::global-message
  (fn [db]
    (and (not (str/blank? (:global-message db)))
         (:global-message db))))

(reg-sub
  ::show-hp-mp-potions-ads-button?
  (fn [db]
    (or (= 0 (-> db :player :hp-potions (or 0)))
        (= 0 (-> db :player :mp-potions (or 0))))))

(reg-sub
  ::show-ui-panel?
  (fn [db]
    (:show-ui-panel? db)))

(reg-sub
  ::servers
  (fn [db]
    (-> db :servers :list)))

(reg-sub
  ::available-servers
  :<- [::servers]
  (fn [servers]
    (->> servers
         vals
         (filter (fn [{:keys [number-of-players max-number-of-players]}]
                   (and number-of-players
                        max-number-of-players
                        (< number-of-players max-number-of-players))))
         (sort-by (juxt :number-of-players :ping) (fn [[np1 p1] [np2 p2]]
                                                    (if (= np1 np2)
                                                      (compare p1 p2)
                                                      (compare np2 np1))))
         seq)))

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

(reg-sub
  ::in-iframe?
  (fn [db]
    (:in-iframe? db)))

(reg-sub
  ::char-panel-open?
  (fn [db]
    (:char-panel-open? db)))

(reg-sub
  ::current-time
  (fn [db]
    (:current-time db)))

(reg-sub
  ::quest
  (fn [db]
    (:quest db)))
