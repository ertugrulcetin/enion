(ns enion-backend.redis
  (:refer-clojure :exclude [set get])
  (:require
    [clojure.string :as str]
    [clojure.tools.logging :as log]
    [taoensso.carmine :as car]))

(defonce my-conn-pool (car/connection-pool {}))
(defonce my-conn-spec-1 {:uri "redis://default:6df1e1ed1b4a4de6a582235b1829dbab@eu2-active-hyena-31054.upstash.io:31054"})

(defonce my-wcar-opts
  {:pool my-conn-pool
   :spec my-conn-spec-1})

(defn set [k v]
  (try
    (car/wcar my-wcar-opts (car/set k v))
    (catch Exception e
      (println e)
      (log/error e)
      (throw e))))

(defn get [k]
  (try
    (car/wcar my-wcar-opts (car/get k))
    (catch Exception e
      (println e)
      (log/error e)
      (throw e))))

(defn update-username [token class username]
  (set token (assoc-in (get token) [class :username] username)))

(defn update-last-played-class [token class]
  (set token (assoc (get token) :last-played-class class)))

(defn update-last-played-race [token race]
  (set token (assoc (get token) :last-played-race race)))

(defn update-bp [players id bp]
  (when-let [player (clojure.core/get @players id)]
    (let [{:keys [token]} player
          class (:class player)]
      (set token (update-in (get token) [class :bp] (fnil + 0) bp)))))

(defn update-exp [token class exp]
  (set token (assoc-in (get token) [class :exp] exp)))

(defn level-up [token class attr]
  (set token (merge (get token) {class attr})))

(comment
  (set "abc" {:username {:name "abc" :password "123"}})
  (get "abc")
  (get "mZJidk9bgI7Mw0VVLHoLv")
  )
