(ns enion-cljs.ui.tutorial
  (:require
    [applied-science.js-interop :as j]
    [common.enion.skills :as common.skills]
    [enion-cljs.common :refer [on fire]]
    [enion-cljs.scene.states :as st]))

(defonce state (atom nil))
(defonce current-intro (atom nil))

(def cast-skills-mapping-by-class
  {"asas" "attackDagger"
   "warrior" "attackOneHand"
   "mage" "attackSingle"
   "priest" "attackPriest"})

(defn- start-intro
  ([steps]
   (start-intro steps nil nil))
  ([steps on-exit show-ui-panel?]
   (when-let [intro (j/call js/window :introJs)]
     (reset! current-intro intro)
     (let [intro (j/call intro :onexit (fn []
                                         (reset! current-intro nil)
                                         (js/setTimeout #(j/assoc! st/settings :tutorial? false) 200)
                                         (fire :ui-show-panel? true)
                                         (fire :on-ui-element? false)
                                         (when on-exit (on-exit))))
           intro (j/call intro :setOptions
                         (clj->js
                           {:tooltipClass "customTooltip"
                            :steps
                            (clj->js steps)}))]
       (j/call intro :start)
       (j/assoc! st/settings :tutorial? true)
       (fire :on-ui-element? true)
       (when-not show-ui-panel?
         (fire :ui-show-panel? false))))))

(defn next-intro []
  (when-let [intro @current-intro]
    (j/call intro :nextStep)))

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
    :intro (str "Find the chest in the forest to get <b>25 health and mana</b> potions.<br/><br/>"
                "You can use the portal located at your base.<br/>"
                "<img src=\"img/chest.png\" style=\"position: relative;left: calc(50% - 90px);top: 20px;\">")}])

(defn how-to-change-skill-order []
  [{:title "How to change skill order?"
    :intro (str "<b>Right-click on any of the skill</b> you'd like to change the order of, "
                "and then click on the spot where you'd like to have it.")
    :element (j/call js/document :getElementById "skill-bar")}])

(defn how-to-cast-skills []
  (let [class (:class @state)
        race (:race @state)
        skill (cast-skills-mapping-by-class class)
        skill-name (-> skill common.skills/skills (get :name))]
    [{:intro (if (= "orc" race)
               (str "Find a <b>Human</b> and <b>select it by clicking on it with your mouse</b>, "
                    "or you can <b>press the Z key</b> to select the closest enemy.<br/><br/> "
                    "Apply <b>" skill-name "</b> by pressing a key on your keyboard or clicking with your mouse.<br/>"
                    "<img src=\"img/enemy_human.jpeg\" style=\"position: relative;left: calc(50% - 70px);top: 20px;\">")
               (str "Find an <b>Orc</b> and <b>select it by clicking on it with your mouse</b>, "
                    "or you can <b>press the Z key</b> to select the closest enemy.<br/><br/> "
                    "Apply <b>" skill-name "</b> by pressing a key on your keyboard or clicking with your mouse.<br/>"
                    "<img src=\"img/enemy_orc.jpeg\" style=\"position: relative;left: calc(50% - 90px);top: 20px;\">"))
      :element (j/call js/document :getElementById (str "skill-" skill))}]))

(defn how-to-rotate-camera-to-the-left []
  [{:title "How to rotate the camera to the left?"
    :intro "Press the <b>Q</b> key to rotate the camera to the left."}])

(defn how-to-rotate-camera-to-the-right []
  [{:title "How to rotate the camera to the right?"
    :intro "Press the <b>E</b> key to rotate the camera to the right."}])

(def tutorials-order
  [[:how-to-navigate? "Navigate with WASD Keys" navigation-steps]
   [:how-to-rotate-camera? "Adjust your camera rotation" how-to-rotate-camera]
   [:rotate-camera-to-the-left "Rotate camera to the left with Key Q" how-to-rotate-camera-to-the-left]
   [:rotate-camera-to-the-right "Rotate camera to the right with Key E" how-to-rotate-camera-to-the-right]
   ;; [:how-to-run-faster? "Run faster with Fleet Foot" how-to-run-faster true]
   [:how-to-use-portal? "Use portal to teleport to the forest" how-to-use-portal]
   [:how-to-cast-skills? "Use your skills to defeat enemies" how-to-cast-skills true]
   [:what-is-the-first-quest? "Get your first quest" what-is-the-first-quest]
   [:how-to-change-skill-order? "Change your skill order" how-to-change-skill-order true]])

(on :ui-init-tutorial-data (fn [data] (reset! state data)))

(comment

  )
