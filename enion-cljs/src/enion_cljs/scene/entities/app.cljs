(ns enion-cljs.scene.entities.app
  (:require
    [applied-science.js-interop :as j]
    [enion-cljs.scene.lod-manager :as lod-manager]
    [enion-cljs.scene.pc :as pc])
  (:require-macros
    [enion-cljs.scene.macros :refer [fnt]]))

(defn- clear-depth-buffer-layer []
  (let [layer (j/call-in pc/app [:scene :layers :getLayerByName] "Clear Depth")]
    (j/assoc! layer :clearDepthBuffer true)))

(defn- update-orc-human-materials []
  (some-> (pc/find-asset-by-name "orc_material") (j/assoc-in! [:resource :blendType] js/pc.BLEND_NONE))
  (some-> (pc/find-asset-by-name "human_material") (j/assoc-in! [:resource :blendType] js/pc.BLEND_NONE)))

(defn- init-fn [this]
  (set! pc/app (j/get this :app))
  (pc/disable-context-menu)
  (update-orc-human-materials)
  (clear-depth-buffer-layer))

(defn init []
  (pc/create-script :app
                    {:init (fnt (init-fn this))
                     :post-init (fn []
                                  (lod-manager/init))}))
