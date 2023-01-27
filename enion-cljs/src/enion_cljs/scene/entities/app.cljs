(ns enion-cljs.scene.entities.app
  (:require
    [applied-science.js-interop :as j]
    ;[enion-cljs.common :as common :refer [dev?]]
    ;[enion-cljs.scene.lod-manager :as lod-manager]
    ;[enion-cljs.scene.pc :as pc]
    ;[enion-cljs.scene.simulation :as simulation]
    ["playcanvas" :as ps]
    ;["/enion_cljs/vendor/__settings__"]
    )
  #_(:require-macros
    [enion-cljs.scene.macros :refer [fnt]]))

(defn- clear-depth-buffer-layer []
  #_(let [layer (j/call-in pc/app [:scene :layers :getLayerByName] "Clear Depth")]
    (j/assoc! layer :clearDepthBuffer true)))

;; We did this due to water blending problem, but had to revert - otherwise we can't update opacity of chars
;; let's keep this code for a while
(defn- update-orc-human-materials []
  #_(some-> (pc/find-asset-by-name "orc_material") (j/assoc-in! [:resource :blendType] js/pc.BLEND_NONE))
  #_(some-> (pc/find-asset-by-name "human_material") (j/assoc-in! [:resource :blendType] js/pc.BLEND_NONE)))

#_(defn- init-fn [this]
  ;(println (ps/Application. ))
  (set! pc/app (j/get this :app))
  (common/set-app pc/app)
  (pc/disable-context-menu)
  (update-orc-human-materials)
  (clear-depth-buffer-layer)
  (common/enable-global-on-listeners))

(defn init [init-ui]
  (println "KEK!")
  (js/console.log ps)
  #_(pc/create-script :app
                    {:init (fnt
                             (init-fn this)
                             (init-ui))
                     :post-init (fn []
                                  (lod-manager/init)
                                  (when dev?
                                    (simulation/init)))}))
