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
  (init-game {:id 1
              :username "NeaTBuSTeR"
              ;; :race "orc"
              :race "orc"
              :class "warrior"
              :mana 100
              :health 100
              ;; :pos [(+ 38 (rand 1)) 0.55 (- (+ 39 (rand 4)))]
              :pos [38.78007355801282 0.55 -41.42730331929569]}))
