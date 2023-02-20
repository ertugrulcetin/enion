(ns enion-cljs.ui.intro
  (:require
    [applied-science.js-interop :as j]
    [enion-cljs.common :refer [on]]
    [enion-cljs.ui.events :as events]
    [re-frame.core :refer [dispatch]]))

(defn start []
  (when-let [intro (j/call js/window :introJs)]
    (let [intro (j/call intro :onexit #(dispatch [::events/update-settings :show-tutorial? false]))
          intro (j/call intro :setOptions
                        (clj->js
                          {:tooltipClass "customTooltip"
                           :steps
                           [{:title "Welcome to Enion Online! \uD83D\uDC4B"
                             :intro "To navigate the game, you can either use the <b>WASD</b> keys, or click and drag the <b>left mouse button</b>."}
                            {:intro "To jump in the game, press the <b>Space</b> bar on your keyboard."}
                            {:intro "To adjust the camera rotation, <b>click and hold the right mouse button</b> and then move your mouse in the direction you want to rotate the camera."}
                            {:element (j/call js/document :querySelector ".enion-cljs-ui-styles-skill-bar")
                             :intro "You can apply your skills by pressing any key between <b>1 and 8</b> on your keyboard."}
                            {:element (j/call js/document :querySelector ".enion-cljs-ui-styles-skill-bar")
                             :intro "You can change the order of your skills by clicking and dragging a skill from one slot in the skill bar to another."}
                            {:element (j/call js/document :querySelector ".enion-cljs-ui-styles-chat-wrapper")
                             :intro (str "You can chat with your allies and party members by using the in-game chat feature.<br/><br/>"
                                         "To use the in-game chat feature, press the <b>Enter</b> key on your keyboard to enable the chat window.")}
                            {:element (j/call js/document :querySelector ".enion-cljs-ui-styles-button.enion-cljs-ui-styles-party-action-button")
                             :intro "You can create a party or invite other players to join your party by selecting a player and sending them a party invitation."}
                            {:element (j/call js/document :getElementById "settings-button")
                             :intro "You can customize game settings such as camera rotation, graphics, and sound by accessing the settings menu."}
                            {:element (j/call js/document :getElementById "temp-container-for-fps-ping-online")
                             :intro (str "You can view your current FPS, ping, and the number of online players. <br/><br/>"
                                         "You can choose to hide or reveal these counters by adjusting your settings.")}]}))]
      (j/call intro :start))))

(on :ui-show-tutorial start)

(comment
  (start)
  )
