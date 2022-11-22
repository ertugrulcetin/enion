(ns enion-cljs.scene.entities.app
  (:require
    [applied-science.js-interop :as j]
    [enion-cljs.scene.pc :as pc])
  (:require-macros
    [enion-cljs.scene.macros :refer [fnt]]))

(defn- init-fn [this]
  (set! pc/app (j/get this :app)))

(defn init []
  (pc/create-script :app
                    {:init (fnt (init-fn this))}))
