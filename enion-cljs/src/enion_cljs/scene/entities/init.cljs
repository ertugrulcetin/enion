(ns enion-cljs.scene.entities.init
  (:require
    ["playcanvas" :as ps]
    [applied-science.js-interop :as j]))

(j/assoc! js/window :pc (or (j/get js/window :pc) ps))
