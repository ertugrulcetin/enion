(ns enion-backend.utils
  (:require
    [enion-backend.config :refer [env]]))

(defn dev? []
  (:dev env))

(defn dissoc-in
  [m [k & ks]]
  (if-not ks
    (dissoc m k)
    (assoc m k (dissoc-in (m k) ks))))

(defn rand-between [min max]
  (int (+ (Math/floor (* (Math/random) (inc (- max min)))) min)))
