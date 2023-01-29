(ns enion-cljs.core
  (:require
    [enion-cljs.common :refer [on]]
    [enion-cljs.scene.core :as scene.core]
    [enion-cljs.ui.core :as ui.core]))

(defn- create-div-app []
  (let [div (js/document.createElement "div")]
    (.setAttribute div "id" "app")
    (.prepend (.-body js/document) div)))

(defn init-game []
  (create-div-app)
  (scene.core/init #(ui.core/init)))

(defn init []
  (init-game))
