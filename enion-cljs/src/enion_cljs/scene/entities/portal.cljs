(ns enion-cljs.scene.entities.portal
  (:require
    [applied-science.js-interop :as j]
    [common.enion.skills :as common.skills]
    [enion-cljs.common :refer [dev? fire on dlog]]
    [enion-cljs.scene.pc :as pc]
    [enion-cljs.scene.states :as st]
    [enion-cljs.scene.utils :as utils]))

(def random-orc-forest-portal-spawn-points
  [[36.95530700683594 1.6 27.271080017089844]
   [36.645931243896484 1.6 28.3499755859375]
   [37.447025299072266 1.6 29.114809036254883]
   [37.53257751464844 1.6 27.182781219482422]
   [36.525081634521484 1.6 26.66585350036621]
   [32.73955154418945 1.6 29.754188537597656]
   [34.4808464050293 1.6 30.419660568237305]
   [35.329307556152344 1.6 29.839717864990234]
   [34.81787109375 1.6 29.308151245117188]
   [34.6307373046875 1.6 28.070968627929688]])

(def random-human-forest-portal-spawn-points
  [[35.36539077758789 1.6 33.74102020263672]
   [34.68355941772461 1.6 34.43633270263672]
   [34.68482971191406 1.6 34.465179443359375]
   [34.06559371948242 1.6 34.58415222167969]
   [33.173789978027344 1.6 33.629005432128906]
   [32.564029693603516 1.6 33.4410400390625]
   [31.76464080810547 1.6 32.92132568359375]
   [31.466880798339844 1.6 33.85318374633789]
   [31.769575119018555 1.6 34.9247932434082]
   [31.163869857788086 1.6 35.61369323730469]])

(defn- trigger-start [portal]
  (cond
    (not (st/finished-quests?))
    (fire :ui-show-global-message "Finish all quests to use the portal!" 3000)

    (and (not dev?) (< (st/get-level) 7))
    (fire :ui-show-global-message "You need to be level 7 to use the portal" 2500)

    :else
    (do
      (dlog "trigger-start")
      (st/play-sound "portal_pass")
      (js/setTimeout
        (fn []
          (case portal
            "orc_portal_model" (st/move-player (first (shuffle random-orc-forest-portal-spawn-points)))
            "human_portal_model" (st/move-player (first (shuffle random-human-forest-portal-spawn-points)))
            "forest_portal_model" (if (= "orc" (st/get-race))
                                    (st/move-player (common.skills/random-pos-for-orc))
                                    (st/move-player (common.skills/random-pos-for-human))))
          (st/cancel-target-pos))
        250))))

(defn- register-portals-trigger-events []
  (let [portals (pc/find-by-name "portals")]
    (doseq [portal [(pc/find-by-name portals "orc_portal_model")
                    (pc/find-by-name portals "human_portal_model")
                    (pc/find-by-name portals "forest_portal_model")]]
      (j/call-in portal [:collision :on] "triggerenter" (fn [] (trigger-start (j/get portal :name)))))))
