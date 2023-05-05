(ns enion-cljs.scene.entities.base
  (:require
    [applied-science.js-interop :as j]
    [enion-cljs.common :refer [fire on]]
    [enion-cljs.scene.network :refer [dispatch-pro]]
    [enion-cljs.scene.pc :as pc]
    [enion-cljs.scene.states :as st]))

(defonce entities (atom []))
(defonce init-forest? (atom false))

(defn- trigger-start []
  (dispatch-pro :trigger-base true))

(defn- trigger-end []
  (dispatch-pro :trigger-base false))

(defn register-base-trigger-events []
  (let [race (st/get-race)
        enemy-base-box (pc/find-by-name (if (= "orc" race) "human_base_trigger" "orc_base_trigger"))
        base-box (pc/find-by-name (if (= "orc" race) "orc_base_trigger" "human_base_trigger"))
        npc-box (pc/find-by-name (if (= "orc" race) "orc_npc_model" "human_npc_model"))]
    (swap! entities conj enemy-base-box npc-box)
    (j/call-in enemy-base-box [:collision :on] "triggerenter" (fn [] (trigger-start)))
    (j/call-in enemy-base-box [:collision :on] "triggerleave" (fn [] (trigger-end)))
    (j/call-in base-box [:collision :on] "triggerleave" (fn []
                                                          (when-not @init-forest?
                                                            (fire :init-forest-entities)
                                                            (reset! init-forest? true))))
    (j/call-in npc-box [:collision :on] "triggerenter" (fn []
                                                         (fire :ui-show-talk-to-npc true)
                                                         (fire :in-npc-zone true)))
    (j/call-in npc-box [:collision :on] "triggerleave" (fn []
                                                         (fire :ui-show-talk-to-npc false)
                                                         (fire :in-npc-zone false))))
  (if-not (st/finished-quests?)
    (let [walls (pc/find-by-name "walls")]
      (pc/enable walls)
      (doseq [e (j/get walls :children)]
        (j/call-in e [:collision :on] "collisionstart"
                   #(fire :ui-show-global-message "Finish all quests to explore map!" 2000))))
    (pc/disable (pc/find-by-name "walls"))))

(defn unregister-base-trigger-events []
  (doseq [base-box @entities]
    (j/call-in base-box [:collision :off]))
  (reset! entities []))

(on :disable-walls
    #(when (st/finished-quests?)
       (pc/disable (pc/find-by-name "walls"))))
