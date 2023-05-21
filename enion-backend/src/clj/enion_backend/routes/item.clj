(ns enion-backend.routes.item
  (:require
    [clojure.set :as set]
    [common.enion.item :as item]
    [enion-backend.async :refer [reg-pro]]
    [enion-backend.redis :as redis]
    [enion-backend.routes.home :refer :all]))

(defn- find-available-slot-id [inventory]
  (->> (set (keys inventory))
       (set/difference (set (range item/inventory-size)))
       sort
       first))

(reg-pro
  :buy-item
  (fn [{:keys [id current-players data]}]
    (let [selected-item (:item data)
          new-item-price (-> item/items selected-item :buy)
          {:keys [token class inventory coin]} (get current-players id)]
      (cond
        (= (count inventory) item/inventory-size)
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
