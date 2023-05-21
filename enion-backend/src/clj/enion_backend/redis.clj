(ns enion-backend.redis
  (:refer-clojure :exclude [set get])
  (:require
    [clojure.tools.logging :as log]
    [enion-backend.utils :refer [dev?]]
    [taoensso.carmine :as car]))

(defonce my-conn-pool (car/connection-pool {}))
(defonce my-conn-spec-1 {:uri "redis://default:6df1e1ed1b4a4de6a582235b1829dbab@eu2-active-hyena-31054.upstash.io:31054"})

(defonce my-wcar-opts
  {:pool my-conn-pool
   :spec my-conn-spec-1})

(defn set [k v]
  (do
    ;; when-not (dev?)
    (try
      (car/wcar my-wcar-opts (car/set k v))
      (catch Exception e
        (println e)
        (log/error e)
        (throw e)))))

(defn get [k]
  (do
    ;; when-not (dev?)
    (try
      (car/wcar my-wcar-opts (car/get k))
      (catch Exception e
        (println e)
        (log/error e)
        (throw e)))))

(defn update-username [token class username]
  (set token (assoc-in (get token) [class :username] username)))

(defn update-last-played-class [token class]
  (set token (assoc (get token) :last-played-class class)))

(defn update-last-played-race [token race]
  (set token (assoc (get token) :last-played-race race)))

(defn update-bp [players id total-bp]
  (when-let [player (clojure.core/get @players id)]
    (let [{:keys [token class]} player]
      (set token (assoc-in (get token) [class :bp] total-bp)))))

(defn update-exp [token class exp]
  (set token (assoc-in (get token) [class :exp] exp)))

(defn update-exp-&-coin [token class exp coin]
  (set token (-> (get token)
                 (assoc-in [class :exp] exp)
                 (assoc-in [class :coin] coin))))

(defn level-up [token class attr]
  (set token (update (get token) class merge attr)))

(defn complete-tutorial [token tutorial]
  (set token (update (get token) :tutorials (fnil conj #{}) tutorial)))

(defn complete-quest [token class quest]
  (set token (update-in (get token) [class :quests] (fnil conj #{}) quest)))

(defn reset-tutorial [token]
  (set token (update (get token) :tutorials #{})))

(defn update-inventory-&-coin [token class inventory coin]
  (set token (-> (get token)
                 (assoc-in [class :inventory] inventory)
                 (assoc-in [class :coin] coin))))

(defn update-item-buy-stats [item]
  (let [key "item-buy-stats"
        stats (get key)]
    (set key (update stats item (fnil inc 0)))))

(defn update-item-sell-stats [item]
  (let [key "item-sell-stats"
        stats (get key)]
    (set key (update stats item (fnil inc 0)))))

(comment
  (set "abc" {:username {:name "abc" :password "123"}})

  (set "4TSkP1l9dArDKEDI8zDQC" (assoc-in (get "4TSkP1l9dArDKEDI8zDQC") ["mage" :level] 29))

  (update-exp-&-coin "Nzrq4Bu0kH9shSIGxSLrd" "asas" 0 1185000)

  (let [token "Nzrq4Bu0kH9shSIGxSLrd"]
    (set token (assoc (get token) :quests #{})))
  )

'{:last-played-class warrior, :last-played-race orc, warrior {:level 5, :exp 36, :coin 1565, :quests #{:squid}}}
