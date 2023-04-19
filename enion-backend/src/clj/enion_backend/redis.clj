(ns enion-backend.redis
  (:refer-clojure :exclude [set get])
  (:require
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
      (log/error e))))

(defn get [k]
  (try
    (car/wcar my-wcar-opts (car/get k))
    (catch Exception e
      (println e)
      (log/error e))))

(defn update-username [token username]
  (set token (assoc (get token) :username username)))

(defn update-bp [players id bp]
  (when-let [player (get-in @players [id])]
    (let [{:keys [token]} player]
      (set token (assoc (get token) :bp bp)))))

(comment
  (set "abc" {:username {:name "abc" :password "123"}})
  (get "abc")
  )
