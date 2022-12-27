(ns enion-cljs.scene.keyboard
  (:require
    [enion-cljs.scene.pc :as pc]))

(defn pressing-wasd? []
  (or (pc/pressed? :KEY_W)
      (pc/pressed? :KEY_A)
      (pc/pressed? :KEY_S)
      (pc/pressed? :KEY_D)))
