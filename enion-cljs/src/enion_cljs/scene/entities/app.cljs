(ns enion-cljs.scene.entities.app
  (:require
   [enion-cljs.scene.entities.init]
   ["/enion_cljs/vendor/all"]
   ["/enion_cljs/vendor/tween" :as tw]
   [enion-cljs.scene.lod-manager]
   [applied-science.js-interop :as j]
   [enion-cljs.common :as common :refer [dev? fire on]]
   [enion-cljs.scene.pc :as pc]
   [enion-cljs.scene.poki :as poki]
   [enion-cljs.scene.simulation :as simulation]
   [enion-cljs.scene.entities.portal :as portal])
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

(on :notify-ui-is-ready
  (fn []
    (fire :start-ws)
    (poki/game-loading-finished)))

(defn visibility-props []
  (cond
    (some? js/document.hidden) {:hidden "hidden"
                                :visibility-change "visibilitychange"}
    (some? js/document.msHidden) {:hidden "msHidden"
                                  :visibility-change "msvisibilitychange"}
    (some? js/document.webkitHidden) {:hidden "webkitHidden"
                                      :visibility-change "webkitvisibilitychange"}
    :else (js/console.warn "visibility prop not found in visibility-props fn")))

;;TODO when unfocus - another tab etc, then show count down from 5 seconds and block everything...
(defn init [init-ui]
  (pc/create-script :app
    {:init (fnt
             (map-pc-vars)
             (init-fn this)
             (init-ui)
             (portal/register-portals-trigger-events))
     :post-init (fn []
                  ;;TODO window.Terrain/Water/Wave acik onlari da null'a setle
                  (when dev?
                    (simulation/init))
                  (when-not dev?
                    (let [fill-mode-none (j/get-in js/window [:pc :FILLMODE_NONE])
                          fill-mode-aspect (j/get-in js/window [:pc :FILLMODE_KEEP_ASPECT])]
                      (j/assoc! js/window :pc nil)
                      (j/assoc-in! js/window [:pc :FILLMODE_NONE] fill-mode-none)
                      (j/assoc-in! js/window [:pc :FILLMODE_KEEP_ASPECT] fill-mode-aspect)))
                  (poki/init)
                  (let [{:keys [hidden visibility-change]} (visibility-props)]
                    (when hidden
                     (js/document.addEventListener visibility-change #(fire :tab-hidden (not (j/get js/document hidden)))))))}))
