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

(defn create-char-name-text [{:keys [template-entity username race class other-player? enemy? y-offset]}]
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
    (when y-offset
      (pc/set-loc-pos username-text-entity 0 y-offset 0))
    (j/assoc-in! username-text-entity [:script :enabled] true)))

(defn add-skill-effects [template-entity]
  (let [effects (pc/clone (pc/find-by-name "effects"))]
    (pc/add-child template-entity effects)
    ;; add skill effect initial counters and related entities
    (->> (map (j/get :name) (j/get effects :children))
         (concat
           ["particle_fire_hands"
            "particle_ice_hands"
            "particle_flame_dots"
            "particle_heal_hands"
            "particle_cure_hands"
            "particle_defense_break_hands"])
         (keep
           (fn [e]
             (when-let [entity (pc/find-by-name template-entity e)]
               [e {:counter 0
                   :entity entity
                   :state #js {:value 0}}])))
         (into {})
         clj->js)))
