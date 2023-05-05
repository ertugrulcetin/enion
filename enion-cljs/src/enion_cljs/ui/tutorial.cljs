(ns enion-cljs.ui.tutorial
  (:require
    [applied-science.js-interop :as j]
    [common.enion.skills :as common.skills]
    [enion-cljs.common :refer [on fire]]
    [enion-cljs.ui.intro :as intro]))

(defonce state (atom nil))

(def cast-skills-mapping-by-class
  {"asas" "attackDagger"
   "warrior" "attackOneHand"
   "mage" "attackSingle"
   "priest" "attackPriest"})

(defn show-unlocked-skill-fleet-foot []
  (intro/start-intro
    [{:title "Skill Unlocked ✨"
      :intro "Use <b>Fleet Foot</b> to run faster!"
      :element (j/call js/document :getElementById "skill-fleetFoot")}]
    nil
    true))

(defn how-to-cast-skills []
  (let [class (:class @state)
        skill (cast-skills-mapping-by-class class)
        skill-name (-> skill common.skills/skills (get :name))]
    [{:intro (str "Use <b>" skill-name "</b> to attack.<br/>")
      :element (j/call js/document :getElementById (str "skill-" skill))}]))

(defn show-unlocked-skill [skill name]
  (intro/start-intro
    [{:title "Skill Unlocked ✨"
      :intro (str "You unlocked skill <b>" name "</b>!")
      :element (j/call js/document :getElementById (str "skill-" skill))}]
    nil
    true))

(def tutorials
  [{:name :navigate-wasd
    :text "Navigate with WASD Keys"
    :action-keys ["W" "A" "S" "D"]}
   {:name :adjust-camera
    :text "Adjust camera with Right Click"
    :img "right-click.png"}
   {:name :select-enemy
    :text "Select closest enemy"
    :action-key "TAB"
    :in-quest? true
    :on-complete (fn []
                   (intro/start-intro (how-to-cast-skills) nil true))}
   {:name :run-to-enemy
    :text "Run at the enemy"
    :action-key "R"
    :in-quest? true}
   {:name :rotate-camera-to-the-left
    :text "Rotate camera to the left"
    :action-key "Q"}
   {:name :rotate-camera-to-the-right
    :text "Rotate camera to the right"
    :action-key "E"}
   {:name :open-character-panel
    :text "Open Character Panel"
    :action-key "C"}
   {:name :open-leader-board
    :text "Open Leader Board"
    :action-key "L"}
   {:name :jump
    :text "Jump"
    :action-key "Space"}])

;; TODO remove this
(on :ui-init-tutorial-data (fn [data] (reset! state data)))

(on :finish-tutorial-progress
    (fn [tutorial]
      (let [{:keys [on-complete]} (first (filter #(= (:name %) tutorial) tutorials))]
        (when on-complete
          (on-complete)))))

(on :show-unlocked-skill-fleet-foot show-unlocked-skill-fleet-foot)
(on :show-unlocked-skill show-unlocked-skill)
