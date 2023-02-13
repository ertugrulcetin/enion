(ns enion-backend.utils)

(defn dissoc-in
  [m [k & ks]]
  (if-not ks
    (dissoc m k)
    (assoc m k (dissoc-in (m k) ks))))
