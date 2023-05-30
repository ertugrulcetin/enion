(ns enion-backend.routes.item
  (:require
    [clojure.set :as set]
    [common.enion.item :as item]
    [enion-backend.async :refer [reg-pro]]
    [enion-backend.redis :as redis]
    [enion-backend.routes.home :refer :all]
    [enion-backend.utils :as utils]))

(defn- find-available-slot-id [inventory]
  (->> inventory
       (keep (fn [[k v]] (when-not (nil? v) k)))
       set
       (set/difference (set (range item/inventory-max-size)))
       sort
       first))

(reg-pro
  :buy-item
  (fn [{:keys [id current-players data]}]
    (let [selected-item (:item data)
          new-item-price (-> item/items selected-item :buy)
          {:keys [token class inventory coin]} (get current-players id)
          inventory-size (->> inventory vals (remove nil?) count)]
      (cond
        (= inventory-size item/inventory-max-size)
        {:err "Your inventory is full!"}

        (> new-item-price coin)
        {:err "You don't have enough coins!"}

        :else
        (let [slot-id (find-available-slot-id inventory)
              inventory (assoc inventory slot-id {:id (str (random-uuid))
                                                  :item selected-item
                                                  :level 1})
              coin (- coin new-item-price)]
          (swap! players (fn [players]
                           (-> players
                               (assoc-in [id :inventory] inventory)
                               (assoc-in [id :coin] coin))))
          (redis/update-inventory-&-coin token class inventory coin)
          (redis/update-item-buy-stats selected-item)
          {:inventory inventory
           :coin coin})))))

(reg-pro
  :sell-item
  (fn [{:keys [id current-players data]}]
    (let [{:keys [slot-id item]} (:item data)
          item-price (-> item/items item :sell)
          {:keys [token class inventory coin]} (get current-players id)]
      (if (not= (get-in inventory [slot-id :item]) item)
        {:err "You don't have this item!"}
        (let [inventory (dissoc inventory slot-id)
              coin (+ coin item-price)]
          (swap! players (fn [players]
                           (-> players
                               (assoc-in [id :inventory] inventory)
                               (assoc-in [id :coin] coin))))
          (redis/update-inventory-&-coin token class inventory coin)
          (redis/update-item-sell-stats item)
          {:inventory inventory
           :coin coin})))))

(reg-pro
  :update-item-order
  (fn [{:keys [id current-players data]}]
    (let [{:keys [idx prev-idx]} data
          {:keys [token class inventory coin]} (get current-players id)
          idx-item (get inventory idx)
          prev-idx-item (get inventory prev-idx)
          inventory (assoc inventory idx prev-idx-item prev-idx idx-item)]
      (swap! players assoc-in [id :inventory] inventory)
      (redis/update-inventory-&-coin token class inventory coin)
      {:inventory inventory
       :coin coin})))

(defn- notify-players-for-equip [id equip]
  (doseq [other-player-id (filter #(not= id %) (keys @world))]
    (send! other-player-id :player-equip {:equip equip
                                          :player-id id})))

(reg-pro
  :equip
  (fn [{:keys [id current-players data]}]
    (let [{:keys [token class inventory equip]} (get current-players id)
          {:keys [weapon shield]} equip
          {:keys [item idx type]} data
          slot-id (find-available-slot-id inventory)
          weapon? (= type :weapon)
          shield? (= type :shield)
          item-weapon? (= :weapon (get-in item/items [(:item item) :type]))
          item-shield? (= :shield (get-in item/items [(:item item) :type]))
          item-class (get-in item/items [(:item item) :class])
          result (cond
                   (or (and item (not (#{:weapon :shield} (get-in item/items [(:item item) :type]))))
                       (and item item-class (not= class (name item-class))))
                   {:err "You can't equip this item!"}

                   (and (nil? item) (or weapon shield) (= (count inventory) item/inventory-max-size))
                   {:err "Your inventory is full!"}


                   (and (nil? item) weapon? weapon)
                   (let [inventory (assoc inventory slot-id weapon)
                         equip (assoc equip :weapon nil)]
                     (swap! players assoc-in [id :inventory] inventory)
                     (swap! players assoc-in [id :equip] equip)
                     (redis/update-inventory-&-equip token class inventory equip)
                     {:inventory inventory
                      :equip equip})

                   (and (nil? item) shield? shield)
                   (let [inventory (assoc inventory slot-id shield)
                         equip (assoc equip :shield nil)]
                     (swap! players assoc-in [id :inventory] inventory)
                     (swap! players assoc-in [id :equip] equip)
                     (redis/update-inventory-&-equip token class inventory equip)
                     {:inventory inventory
                      :equip equip})

                   (and item item-weapon? weapon weapon? idx)
                   (let [inventory (assoc inventory idx weapon)
                         equip (assoc equip :weapon item)]
                     (swap! players assoc-in [id :inventory] inventory)
                     (swap! players assoc-in [id :equip] equip)
                     (redis/update-inventory-&-equip token class inventory equip)
                     {:inventory inventory
                      :equip equip})

                   (and item item-shield? shield shield? idx)
                   (let [inventory (assoc inventory idx shield)
                         equip (assoc equip :shield item)]
                     (swap! players assoc-in [id :inventory] inventory)
                     (swap! players assoc-in [id :equip] equip)
                     (redis/update-inventory-&-equip token class inventory equip)
                     {:inventory inventory
                      :equip equip})


                   (and item item-weapon? (nil? weapon) weapon? idx)
                   (let [inventory (dissoc inventory idx)
                         equip (assoc equip :weapon item)]
                     (swap! players assoc-in [id :inventory] inventory)
                     (swap! players assoc-in [id :equip] equip)
                     (redis/update-inventory-&-equip token class inventory equip)
                     {:inventory inventory
                      :equip equip})


                   (and item item-shield? (nil? shield) shield? idx)
                   (let [inventory (dissoc inventory idx)
                         equip (assoc equip :shield item)]
                     (swap! players assoc-in [id :inventory] inventory)
                     (swap! players assoc-in [id :equip] equip)
                     (redis/update-inventory-&-equip token class inventory equip)
                     {:inventory inventory
                      :equip equip}))]
      (notify-players-for-equip id (get-in @players [id :equip]))
      result)))

(def upgrade-success-probs
  {1 0.95
   2 0.9
   3 0.65
   4 0.55
   5 0.4
   6 0.2
   7 0.05
   8 0.01})

(reg-pro
  :upgrade-item
  (fn [{:keys [id current-players data]}]
    (let [item (:item data)
          {:keys [level]} item
          {:keys [item-idx scroll-idx]} data
          {:keys [token class inventory]} (get current-players id)
          success? (utils/prob? (get upgrade-success-probs level))
          inventory (if success?
                      (assoc inventory item-idx (assoc item :level (inc level)))
                      (dissoc inventory item-idx))
          inventory (dissoc inventory scroll-idx)]
      (redis/update-inventory token class inventory)
      (swap! players assoc-in [id :inventory] inventory)
      {:inventory inventory
       :success? success?})))
