(ns enion-cljs.scene.utils
  (:require
    [applied-science.js-interop :as j]))

(defn rand-between [min max]
  (+ (Math/floor (* (Math/random) (+ (- max min) 1))) min))

(defn tab-visible? []
  (and (not (j/get js/document :hidden))
       (= "visible" (j/get js/document :visibilityState))))
