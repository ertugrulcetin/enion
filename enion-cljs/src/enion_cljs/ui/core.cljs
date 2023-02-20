(ns enion-cljs.ui.core
  (:require
    ["@sentry/browser" :as Sentry]
    ["@sentry/tracing" :refer [BrowserTracing]]
    [applied-science.js-interop :as j]
    [breaking-point.core :as bp]
    [enion-cljs.common :refer [dev?]]
    [enion-cljs.ui.config :as config]
    [enion-cljs.ui.events :as events]
    [enion-cljs.ui.views :as views]
    [re-frame.core :as re-frame]
    [reagent.dom :as rdom]))

;; (def commit-sha (macros/get-env "ENION_SHA"))

(defn dev-setup []
  (when config/debug?
    (println "dev mode")))

(defn ^:dev/after-load mount-root []
  (re-frame/clear-subscription-cache!)
  (let [root-el (.getElementById js/document "app")]
    (rdom/unmount-component-at-node root-el)
    (rdom/render [views/main-panel] root-el)))

(defn- init-sentry []
  (do
    (println "init sentry")
    ;; when-not dev?
    (.init Sentry #js{:dsn "https://f19d5f05ed8e4a778295b47108dddb14@o4504713579724800.ingest.sentry.io/4504713587851264"
                      ;; :release commit-sha
                      ;; :environment (if (not dev?) "production" "dev")
                      :integrations #js [(new BrowserTracing)]
                      :tracesSampleRate 1.0})))

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
  (mount-root)
  (init-sentry))
