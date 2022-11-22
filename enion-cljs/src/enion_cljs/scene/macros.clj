(ns enion-cljs.scene.macros
  (:require
    [applied-science.js-interop :as j]))

(defmacro fnt [& body]
  `(fn [~'dt]
     (cljs.core/this-as ~'this ~@body)))

(defmacro get! [k]
  `(j/get ~'this ~k))

(defmacro get-in! [& ks]
  `(j/get-in ~'this ~(vec ks)))

(defmacro set! [& ks]
  `(j/assoc! ~'this ~@ks))

(defmacro update! [k f & args]
  `(j/update! ~'this ~k ~f ~@args))
