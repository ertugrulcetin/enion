(ns enion-cljs.scene.macros
  (:refer-clojure :exclude [assoc! get! update!]))

(defmacro fnt [& body]
  `(fn [~'dt]
     (cljs.core/this-as ~'this ~@body)))

(defmacro assoc!
  ([obj k v]
   `(do
      (cljs.core/unchecked-set ~obj ~(name k) ~v)
      ~obj))
  ([obj k1 v1 k2 v2]
   `(do
      (assoc! ~obj ~k1 ~v1)
      (assoc! ~obj ~k2 ~v2)))
  ([obj k1 v1 k2 v2 k3 v3]
   `(do
      (assoc! ~obj ~k1 ~v1)
      (assoc! ~obj ~k2 ~v2)
      (assoc! ~obj ~k3 ~v3))))

(defmacro get!
  ([obj k]
   `(cljs.core/unchecked-get ~obj ~(name k)))
  ([obj k1 k2]
   `(-> ~obj (get! ~k1) (get! ~k2)))
  ([obj k1 k2 k3]
   `(-> ~obj (get! ~k1) (get! ~k2) (get! ~k3))))

(defmacro update!
  ([obj k f]
   `(assoc! ~obj ~k (~f (get! ~obj ~k))))
  ([obj k f x]
   `(assoc! ~obj ~k (~f (get! ~obj ~k) ~x)))
  ([obj k f x y]
   `(assoc! ~obj ~k (~f (get! ~obj ~k) ~x ~y))))

(defmacro update-in!
  ([obj k1 k2 f]
   `(do
      (assoc! (get! ~obj ~k1) ~k2 (~f (get! ~obj ~k1 ~k2)))
      ~obj))
  ([obj k1 k2 f x]
   `(do
      (assoc! (get! ~obj ~k1) ~k2 (~f (get! ~obj ~k1 ~k2) x))
      ~obj)))
