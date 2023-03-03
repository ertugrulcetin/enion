(ns enion-cljs.scene.utils
  (:require
    [applied-science.js-interop :as j]
    [enion-cljs.common :refer [fire on]]
    [enion-cljs.scene.states :as st]
    [enion-cljs.utils :as common.utils]))

(defn rand-between [min max]
  (+ (Math/floor (* (Math/random) (+ (- max min) 1))) min))

(defn tab-visible? []
  (and (not (j/get js/document :hidden))
       (= "visible" (j/get js/document :visibilityState))))

(defn set-item [k v]
  (when-let [ls (common.utils/get-local-storage)]
    (.setItem ls k v)))

(defn finish-tutorial-step [tutorial-step]
  (j/update! st/player :tutorials assoc tutorial-step true)
  (st/play-sound "progress")
  (fire :ui-finish-tutorial-progress tutorial-step)
  (set-item "tutorials" (pr-str (j/get st/player :tutorials))))

(defn tutorial-finished? [tutorial-step]
  (tutorial-step (j/get st/player :tutorials)))

(on :finish-tutorial-step finish-tutorial-step)
