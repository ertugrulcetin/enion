(ns enion-cljs.scene.entities.app
  (:require
   [enion-cljs.scene.entities.init]
   ["/enion_cljs/vendor/all"]
   ["/enion_cljs/vendor/tween" :as tw]
   [enion-cljs.scene.lod-manager]
   [applied-science.js-interop :as j]
   [enion-cljs.common :as common :refer [dev? fire]]
   [enion-cljs.scene.pc :as pc]
   [enion-cljs.scene.simulation :as simulation])
  (:require-macros
   [enion-cljs.scene.macros :refer [fnt]]))

(comment
  (println enion.skills)
  )

(defn- clear-depth-buffer-layer []
  (let [layer (j/call-in pc/app [:scene :layers :getLayerByName] "Clear Depth")]
    (j/assoc! layer :clearDepthBuffer true)))

(defn- init-fn [this]
  (set! pc/app (j/get this :app))
  (common/set-app pc/app)
  (pc/disable-context-menu)
  (clear-depth-buffer-layer)
  (common/enable-global-on-listeners))

(defn- map-pc-vars []
  (set! pc/linear js/pc.Linear)
  (set! pc/expo-in js/pc.ExponentialIn))

(defn init [init-ui]
  (pc/create-script :app
    {:init (fnt
             (map-pc-vars)
             (init-fn this)
             (init-ui)
             #_(init {:id 1
                    :username "NeaTBuSTeR"
                    :race "orc"
                    :class "warrior"
                    :health 1000
                    :mana 1000}))
     :post-init (fn []
                  ;;TODO window.Terrain/Water/Wave acik onlari da null'a setle
                  (j/assoc-in! pc/app [:graphicsDevice :maxPixelRatio] 0.75)
                  (when dev?
                    (simulation/init))
                  (when-not dev?
                    (j/assoc! js/window :pc nil))
                  (fire :start-ws))}))
