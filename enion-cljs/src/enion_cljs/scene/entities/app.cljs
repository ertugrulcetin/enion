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
   [enion-cljs.scene.entities.portal :as portal]
   [enion-cljs.scene.states :as st])
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
    (poki/game-loading-finished)))

(on :init-not-preloaded-entities
  (fn []
    (pc/enable (pc/find-by-name "towns"))
    (js/setTimeout #(pc/enable (pc/find-by-name "portals")) 2000)))

(on :init-forest-entities
  (fn []
    (js/setTimeout #(pc/enable (pc/find-by-name "forest")) 1000)))

(defn visibility-props []
  (cond
    (some? js/document.hidden) {:hidden "hidden"
                                :visibility-change "visibilitychange"}
    (some? js/document.msHidden) {:hidden "msHidden"
                                  :visibility-change "msvisibilitychange"}
    (some? js/document.webkitHidden) {:hidden "webkitHidden"
                                      :visibility-change "webkitvisibilitychange"}
    :else (js/console.warn "visibility prop not found in visibility-props fn")))

(defn- optimize-animation-on-update []
  (j/call-in pc/app [:systems :off] "animationUpdate")
  (j/call-in pc/app [:systems :on] "animationUpdate"
    (fn [dt]
      (let [components (j/get-in pc/app [:systems :anim :store])]
        (doseq [id (js/Object.keys components)]
          (when (j/call components :hasOwnProperty id)
            (let [player-id (j/get-in components [id :entity :player_id])
                  npc-id (j/get-in components [id :entity :npc_id])
                  entity (j/get-in components [id :entity])
                  component (j/get entity :anim)
                  component-data (j/get component :data)]
              (when (and (j/get component-data :enabled)
                      (j/get-in component [:entity :enabled])
                      (j/get component :playing))
                (if-let [id (or player-id npc-id)]
                  (let [distance (st/distance-to-player id (boolean npc-id))
                        fixed-timestep (cond
                                         (< distance 5) 0
                                         (< distance 8) 0.05
                                         (< distance 10) 0.1
                                         (< distance 12) 0.15
                                         (< distance 15) 0.2
                                         (< distance 17) 0.25)
                        fixed-timer (j/get component :fixedTimer 0.0)
                        _ (j/assoc! component :fixedTimer (+ fixed-timer dt))
                        fixed-timer (j/get component :fixedTimer)]
                    (when (>= fixed-timer fixed-timestep)
                      (j/call component :update fixed-timer)
                      (j/assoc! component :fixedTimer 0.0)))
                  (j/call component :update dt))))))))))

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
                      (j/assoc! js/window :pc (seq {}))
                      (j/assoc-in! js/window [:pc :FILLMODE_NONE] fill-mode-none)
                      (j/assoc-in! js/window [:pc :FILLMODE_KEEP_ASPECT] fill-mode-aspect)))
                  (poki/init)
                  (let [{:keys [hidden visibility-change]} (visibility-props)]
                    (when hidden
                      (js/document.addEventListener visibility-change #(fire :tab-hidden (boolean (j/get js/document hidden))))))
                  (optimize-animation-on-update))}))
