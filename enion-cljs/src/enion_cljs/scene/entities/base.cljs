(ns enion-cljs.scene.entities.base
  (:require
    [applied-science.js-interop :as j]
    [enion-cljs.scene.network :refer [dispatch-pro]]
    [enion-cljs.scene.pc :as pc]
    [enion-cljs.scene.states :as st]))

(defonce base-entity (atom nil))

(defn- trigger-start []
  (dispatch-pro :trigger-base true))

(defn- trigger-end []
  (dispatch-pro :trigger-base false))

(defn register-base-trigger-events []
  (let [race (st/get-race)
        base-box (pc/find-by-name (if (= "orc" race) "human_base_trigger" "orc_base_trigger"))]
    (reset! base-entity base-box)
    (j/call-in base-box [:collision :on] "triggerenter" (fn [] (trigger-start)))
    (j/call-in base-box [:collision :on] "triggerleave" (fn [] (trigger-end)))))

(defn unregister-base-trigger-events []
  (when-let [base-box @base-entity]
    (j/call-in base-box [:collision :off])))
