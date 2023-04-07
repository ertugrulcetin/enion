(ns enion-cljs.scene.utils
  (:require
    [applied-science.js-interop :as j]
    [enion-cljs.common :refer [fire on]]
    [enion-cljs.scene.pc :as pc]
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

(defn create-char-name-text [{:keys [template-entity username race class other-player? enemy? npc?]}]
  (let [username-text-entity (pc/clone (pc/find-by-name "char_name"))]
    (j/assoc-in! username-text-entity [:element :text] username)
    (j/assoc-in! username-text-entity [:element :color] st/username-color)
    (when enemy?
      (j/assoc-in! username-text-entity [:element :color] st/username-enemy-color))
    (j/assoc-in! username-text-entity [:element :outlineThickness] 0.5)
    (pc/add-child template-entity username-text-entity)
    (when (and (= race "orc")
               (or (= class "priest")
                   (= class "warrior")))
      (when other-player?
        (pc/set-loc-pos username-text-entity 0 0.05 0)))
    (when npc?
      (pc/set-loc-pos username-text-entity 0 0.3 0))
    (j/assoc-in! username-text-entity [:script :enabled] true)))
