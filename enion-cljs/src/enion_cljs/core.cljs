(ns enion-cljs.core
  (:require
   [enion-cljs.ui.core :as ui.core]
   [enion-cljs.scene.core :as scene.core]))

(defn- create-div-app []
  (let [div (js/document.createElement "div")]
    (.setAttribute div "id" "app")
    (.appendChild (.-body js/document) div)))

(defn init []
  (create-div-app)
  (ui.core/init)
  (scene.core/init))
