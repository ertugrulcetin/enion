(ns enion-cljs.scene.entities.app
  (:require
    [applied-science.js-interop :as j]
    [enion-cljs.scene.lod-manager :as lod-manager]
    [enion-cljs.scene.pc :as pc])
  (:require-macros
    [enion-cljs.scene.macros :refer [fnt]]))

(defn- init-fn [this]
  (set! pc/app (j/get this :app))
  (pc/disable-context-menu))

(defn init []
  (pc/create-script :app
                    {:init (fnt (init-fn this))
                     :post-init (fn []
                                  (lod-manager/init))}))
