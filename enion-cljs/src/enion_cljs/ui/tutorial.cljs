(ns enion-cljs.ui.tutorial
  (:require
    [applied-science.js-interop :as j]
    [common.enion.skills :as common.skills]
    [enion-cljs.common :refer [on]]))

(defonce state (atom nil))

(def cast-skills-mapping-by-class
  {"asas" "attackDagger"
   "warrior" "attackOneHand"
   "mage" "attackSingle"
   "priest" "breakDefense"})

(defn- start-intro
  ([steps]
   (start-intro steps nil))
  ([steps on-exit]
   (when-let [intro (j/call js/window :introJs)]
     (let [intro (if on-exit (j/call intro :onexit on-exit) intro)
           intro (j/call intro :setOptions
                         (clj->js
                           {:tooltipClass "customTooltip"
                            :steps
                            (clj->js steps)}))]
       (j/call intro :start)))))

(defn navigation-steps []
  [{:title "Welcome to Enion Online!"
    :intro (str "To navigate the game, you can either use the <b>WASD</b> keys.<br/><br/>"
                "Or click and drag the <b>LEFT MOUSE</b> button.")}
   {:intro "Press the <b>SPACE</b> bar to jump!"}])

(defn how-to-rotate-camera []
  [{:title "How to rotate the camera?"
    :intro (str "<b>Click</b> and <b>hold</b> the <b>RIGHT MOUSE</b> button to adjust the camera rotation.<br/>"
                "<img src=\"img/rightmousebutton.jpeg\" style=\"position: relative;left: calc(50% - 45px);top: 20px;\">")}])

(defn how-to-run-faster []
  [{:title "How to run faster?"
    :intro "You can use the <b>Fleet Foot</b> skill to run faster."
    :element (j/call js/document :getElementById "skill-fleetFoot")}])

(defn how-to-use-portal []
  [{:title "How to use a portal?"
    :intro (str "You can use a <b>portal</b> to teleport to the forest, and then teleport back to your base."
                "<img src=\"img/portal.png\" style=\"position: relative;left: calc(50% - 90px);top: 20px;\">")}])

(defn what-is-the-first-quest []
  [{:title "First quest!"
    :intro (str "Find the chest in the forest to get <b>30 health and mana</b> potions.<br/><br/>"
                "You can use the portal located at your base.<br/>"
                "<img src=\"img/chest.png\" style=\"position: relative;left: calc(50% - 90px);top: 20px;\">")}])

(defn how-to-cast-skills []
  (let [class (:class @state)
        race (:race @state)
        skill (cast-skills-mapping-by-class class)
        skill-name (-> skill common.skills/skills (get :name))]
    [{:title "How to cast skills?"
      :intro (if (= "orc" race)
               (str "Find a <b>Human</b> and <b>select it by clicking on it with your mouse</b>, "
                    "or you can <b>press the Z key</b> to select the closest enemy.<br/><br/> "
                    "Apply <b>" skill-name "</b> by pressing a key on your keyboard or clicking with your mouse.<br/>"
                    "<img src=\"img/enemy_human.jpeg\" style=\"position: relative;left: calc(50% - 70px);top: 20px;\">")
               (str "Find an <b>Orc</b> and <b>select it by clicking on it with your mouse</b>, "
                    "or you can <b>press the Z key</b> to select the closest enemy.<br/><br/> "
                    "Apply <b>" skill-name "</b> by pressing a key on your keyboard or clicking with your mouse.<br/>"
                    "<img src=\"img/enemy_orc.jpeg\" style=\"position: relative;left: calc(50% - 90px);top: 20px;\">"))
      :element (j/call js/document :getElementById (str "skill-" skill))}]))

(def tutorials-order
  [[:how-to-rotate-camera? "Adjust your camera rotation" how-to-rotate-camera]
   [:how-to-run-faster? "Run faster with Fleet Foot" how-to-run-faster]
   [:how-to-use-portal? "Use portal to teleport to the forest" how-to-use-portal]
   [:how-to-cast-skills? "Use your skills to defeat enemies" how-to-cast-skills]
   [:what-is-the-first-quest? "Get you first quest" what-is-the-first-quest]])

(on :ui-init-tutorial-data (fn [data] (reset! state data)))
(on :ui-start-navigation-steps (fn [] (start-intro (navigation-steps))))

(comment

  )
