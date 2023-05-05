(ns enion-cljs.scene.text
  (:require
    [applied-science.js-interop :as j]
    [enion-cljs.common :refer [on]]
    [enion-cljs.scene.pc :as pc]
    [enion-cljs.scene.states :as st]))

(defonce text-pool #js [])
(defonce text-pool-size 20)

(defn- get-text-entity []
  (if-let [text (j/call text-pool :pop)]
    text
    (let [text (pc/clone (pc/find-by-name "text"))]
      (pc/disable text)
      (pc/add-child (j/get st/player :template-entity) text)
      text)))

(defn init-pool []
  (j/assoc! text-pool :length 0)
  (dotimes [_ text-pool-size]
    (let [text (pc/clone (pc/find-by-name "text"))]
      (pc/disable text)
      (pc/add-child (j/get st/player :template-entity) text)
      (j/call text-pool :push text))))

(def default (pc/color (/ 225 255) (/ 222 255) (/ 222 255)))
(def warning (pc/color (/ 255 255) (/ 195 255) (/ 1 255)))
(def damage (pc/color 2 0 0))
(def bp (pc/color (/ 150 255) (/ 150 255) (/ 232 255)))
(def hp-recover (pc/color (/ 83 255) (/ 178 255) (/ 38 255)))
(def mp-recover (pc/color (/ 38 255) (/ 145 255) (/ 178 255)))
(def exp (pc/color (/ 255 255) (/ 250 255) (/ 0 255)))
(def purple (pc/color (/ 131 255) (/ 47 255) (/ 189 255)))

(def temp (pc/vec3))

(defn- random-value []
  (* 2 (- 0.5 (rand))))

(defn- show-text
  ([text]
   (show-text text default))
  ([text color]
   (show-text text color false))
  ([text color info?]
   (show-text text color info? 1))
  ([text color info? secs]
   (let [text-entity (get-text-entity)
         _ (j/assoc-in! text-entity [:element :text] text)
         _ (j/assoc-in! text-entity [:element :color] color)
         initial-pos (pc/get-loc-pos text-entity)
         _ (pc/copyv temp initial-pos)
         last-pos (pc/addv temp (if info?
                                  (pc/vec3 0 0.5 0)
                                  (pc/vec3 (random-value) 0.5 0)))
         tween-interpolation (-> (j/call text-entity :tween initial-pos)
                                 (j/call :to last-pos secs pc/linear))]
     (j/call tween-interpolation :on "update"
             (fn []
               (j/update-in! text-entity [:element :opacity] * 0.99)
               (pc/scale (j/get text-entity :localScale) 0.99)))
     (j/call tween-interpolation :on "complete"
             (fn []
               (pc/disable text-entity)
               (if info?
                 (pc/set-loc-pos text-entity 0 (- 0.5 (rand)) 0)
                 (pc/set-loc-pos text-entity (- 0.5 (rand)) 0 (- 0.5 (rand))))
               (j/call text-pool :push text-entity)
               (pc/disable text-entity)))
     (pc/enable text-entity)
     (j/call text-entity :setLocalScale 0.005 0.005 0.005)
     (j/assoc-in! text-entity [:element :opacity] 1)
     (pc/enable text-entity)
     (j/call tween-interpolation :start)
     nil)))

(on :show-text
    (fn [msg]
      (cond
        (:damage msg) (show-text (str "-" (:damage msg)) damage)
        (:hit msg) (show-text (:hit msg) default)
        (:lost-exp msg) (show-text (str "Lost XP: " (:lost-exp msg) "!") warning 3)
        (:mp-used msg) (show-text (:mp-used msg) warning)
        (:too-far msg) (show-text "Too far!" warning true)
        (:joined-party msg) (show-text (str (:joined-party msg) " joined party") default true)
        (:hp msg) (show-text (:hp msg) hp-recover)
        (:mp msg) (show-text (:mp msg) mp-recover)
        (:bp msg) (show-text (str "BP: " (:bp msg)) bp false 2)
        (:not-enough-mana msg) (show-text "Not enough mana!" warning true)
        (:cure msg) (show-text "Cure" hp-recover true)
        (:defense-break msg) (show-text "Poisoned" purple true)
        (:break-defense msg) (show-text "Poisoned" default true)
        (:party-cancelled msg) (show-text "Party cancelled!" default true 2)
        (:re-spawn-error msg) (show-text "Re-spawn failed!" warning true 2)
        (:party-request-failed msg) (show-text "Party request failed" warning true 2)
        (:member-removed-from-party msg) (show-text (str (:member-removed-from-party msg) " removed from the party") default true 2)
        (:member-exit-from-party msg) (show-text (str (:member-exit-from-party msg) " exit from the party") default true 2)
        (:no-selected-player msg) (show-text "Select player!" warning true)
        (:party-requested-user msg) (show-text (str "Sent party invite to" (:party-requested-user msg)) default true 2)
        (:party-request-rejected msg) (show-text (str (:party-request-rejected msg) " rejected party request") default true 2)
        (:npc-exp msg) (show-text (str "XP: " (:npc-exp msg)) exp false 2)
        (:ping-high msg) (show-text "Ping high!" warning true)
        (:skill-failed msg) (show-text "Skill failed" warning true)
        (:drop msg) (show-text (str (-> msg :drop :amount) " " (-> msg :drop :name)) default true 2)
        (:pvp-locked msg) (show-text "Reach level 10 for PvP!" warning true 2)
        (:enemy-low-level msg) (show-text "Enemy below level 10, can't attack!" warning true 2))))

(comment
  (count text-pool)
  (init-pool)
  (show-text "-720")
  )
