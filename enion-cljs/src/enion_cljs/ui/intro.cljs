(ns enion-cljs.ui.intro
  (:require
    [applied-science.js-interop :as j]
    [enion-cljs.common :refer [on fire]]
    [enion-cljs.scene.states :as st]))

(defonce current-intro (atom nil))

(defn start-intro
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
                            :steps steps
                            :doneLabel "OK"}))]
       (j/call intro :start)
       (j/assoc! st/settings :tutorial? true)
       (fire :on-ui-element? true)
       (when-not show-ui-panel?
         (fire :ui-show-panel? false))))))

(defn next-step []
  (when-let [intro @current-intro]
    (j/call intro :nextStep)))
