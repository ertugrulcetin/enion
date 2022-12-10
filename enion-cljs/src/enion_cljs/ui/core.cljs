(ns enion-cljs.ui.core
  (:require
    [applied-science.js-interop :as j]
    [breaking-point.core :as bp]
    [enion-cljs.ui.config :as config]
    [enion-cljs.ui.events :as events]
    [enion-cljs.ui.views :as views]
    [re-frame.core :as re-frame]
    [reagent.dom :as rdom]))

(defn dev-setup []
  (when config/debug?
    (println "dev mode")))

(defn ^:dev/after-load mount-root []
  (re-frame/clear-subscription-cache!)
  (let [root-el (.getElementById js/document "app")]
    (rdom/unmount-component-at-node root-el)
    (rdom/render [views/main-panel] root-el)))

(defn init []
  (j/call js/window :addEventListener "contextmenu" #(.preventDefault %))
  (re-frame/dispatch-sync [::events/initialize-db])
  (re-frame/dispatch-sync [::bp/set-breakpoints
                           {:breakpoints [:mobile
                                          768
                                          :tablet
                                          992
                                          :small-monitor
                                          1200
                                          :large-monitor]
                            :debounce-ms 166}])
  (dev-setup)
  (mount-root))
