(ns enion-cljs.core
  (:require
    [enion-cljs.scene.core :as scene.core]
    [enion-cljs.ui.core :as ui.core]))

(defn- create-div-app []
  (let [div (js/document.createElement "div")]
    (.setAttribute div "id" "app")
    (.prepend (.-body js/document) div)))

(defn init-game [player-data]
  (create-div-app)
  (scene.core/init #(ui.core/init) player-data))

(defn init []
  ;; (create-div-app)
  ;; (scene.core/init #(ui.core/init))
  )
