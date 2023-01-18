(ns enion-cljs.scene.entities.app
  (:require
    [applied-science.js-interop :as j]
    [enion-cljs.common :as common]
    [enion-cljs.scene.lod-manager :as lod-manager]
    [enion-cljs.scene.pc :as pc])
  (:require-macros
    [enion-cljs.scene.macros :refer [fnt]]))

(defn- clear-depth-buffer-layer []
  (let [layer (j/call-in pc/app [:scene :layers :getLayerByName] "Clear Depth")]
    (j/assoc! layer :clearDepthBuffer true)))

;; We did this due to water blending problem, but had to revert - otherwise we can't update opacity of chars
;; let's keep this code for a while
(defn- update-orc-human-materials []
  #_(some-> (pc/find-asset-by-name "orc_material") (j/assoc-in! [:resource :blendType] js/pc.BLEND_NONE))
  #_(some-> (pc/find-asset-by-name "human_material") (j/assoc-in! [:resource :blendType] js/pc.BLEND_NONE)))

(defn- init-fn [this]
  (set! pc/app (j/get this :app))
  (common/set-app pc/app)
  (pc/disable-context-menu)
  (update-orc-human-materials)
  (clear-depth-buffer-layer))

(defn init [init-ui]
  (pc/create-script :app
                    {:init (fnt
                             (init-fn this)
                             (init-ui))
                     :post-init (fn []
                                  (lod-manager/init))}))
