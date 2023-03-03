(ns enion-cljs.utils
  (:require
    [applied-science.js-interop :as j]))

(defn get-local-storage []
  (try
    (j/get js/window :localStorage)
    (catch js/Error _
      nil)))
