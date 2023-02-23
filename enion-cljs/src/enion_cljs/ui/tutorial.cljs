(ns enion-cljs.ui.tutorial
  (:require
    [applied-science.js-interop :as j]
    [enion-cljs.common :refer [on]]
    [enion-cljs.ui.events :as events]
    [re-frame.core :refer [dispatch]]))

(defn- navigation-steps []
  [{:title "Welcome to Enion Online!"
    :intro (str "To navigate the game, you can either use the <b>WASD</b> keys.<br/><br/>"
                "Or click and drag the <b>LEFT MOUSE</b> button.")}
   {:intro "To jump in the game, press the <b>SPACE</b> bar on your keyboard."}])

(defn camera-steps []
  [{:intro (str "<b>Click</b> and <b>hold</b> the <b>RIGHT MOUSE</b> button to adjust the camera rotation."
                " Move your mouse in the direction you want.")}])

(defn start []
  (when-let [intro (j/call js/window :introJs)]
    (let [intro (j/call intro :onexit #(dispatch [::events/update-settings :show-tutorial? false]))
          intro (j/call intro :setOptions
                        (clj->js
                          {:tooltipClass "customTooltip"
                           :steps
                           (clj->js (navigation-steps))}))]
      (j/call intro :start))))

(on :ui-show-tutorial start)

(comment
  (start)


  {:element (j/call js/document :getElementById "skill-bar")
   :intro "You can apply your skills by pressing any key between <b>1 and 8</b> on your keyboard."}
  {:element (j/call js/document :getElementById "skill-bar")
   :intro "You can change the order of your skills by clicking and dragging a skill from one slot in the skill bar to another."}
  {:element (j/call js/document :getElementById "chat-wrapper")
   :intro (str "You can chat with your allies and party members by using the in-game chat feature.<br/><br/>"
            "To use the in-game chat feature, press the <b>Enter</b> key on your keyboard to enable the chat window.")}
  {:element (j/call js/document :getElementById "add-to-party")
   :intro "You can create a party or invite other players to join your party by selecting a player and sending them a party invitation."}
  {:element (j/call js/document :getElementById "settings-button")
   :intro "You can customize game settings such as camera rotation, graphics, and sound by accessing the settings menu."}
  {:element (j/call js/document :getElementById "temp-container-for-fps-ping-online")
   :intro (str "You can view your current FPS, ping, and the number of online players. <br/><br/>"
            "You can choose to hide or reveal these counters by adjusting your settings.")}
  )
