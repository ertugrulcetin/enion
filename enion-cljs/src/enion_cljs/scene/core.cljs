(ns enion-cljs.scene.core
  (:require
    [enion-cljs.scene.entities.app :as entity.root]
    [enion-cljs.scene.entities.camera :as entity.camera]
    [enion-cljs.scene.entities.player :as entity.player]
    [enion-cljs.scene.skills.effects :as entity.effects]))

(defn init [init-ui]
  (entity.root/init init-ui)
  ;; (entity.player/init player-data)
  (entity.camera/init)
  (entity.effects/init))

(comment
  (do
    (shadow/watch :app)
    (shadow/repl :app)))
