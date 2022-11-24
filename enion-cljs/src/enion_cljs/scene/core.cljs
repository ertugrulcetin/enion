(ns enion-cljs.scene.core
  (:require
    [enion-cljs.scene.entities.app :as entity.root]
    [enion-cljs.scene.entities.camera :as entity.camera]
    [enion-cljs.scene.entities.player :as entity.player]))

(defn init []
  (entity.root/init)
  (entity.player/init)
  (entity.camera/init))

(comment
  (do
    (shadow/watch :app)
    (shadow/repl :app)))
