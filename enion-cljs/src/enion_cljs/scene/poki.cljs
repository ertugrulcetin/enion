(ns enion-cljs.scene.poki
  (:require
    [applied-science.js-interop :as j]
    [enion-cljs.common :refer [dev? on fire]]))

(defn init []
  (when (j/get js/window :PokiSDK)
    (-> (j/call-in js/window [:PokiSDK :init])
        (.then #(js/console.log "Poki SDK successfully initialized"))
        (.catch #(js/console.log "Initialized, but the user likely has adblock")))
    (j/call-in js/window [:PokiSDK :setDebug] dev?)))

(defn game-loading-finished []
  (when (j/get js/window :PokiSDK)
    (j/call-in js/window [:PokiSDK :gameLoadingFinished])))

(defn gameplay-start []
  (when (j/get js/window :PokiSDK)
    (j/call-in js/window [:PokiSDK :gameplayStart])))

(defn gameplay-stop []
  (when (j/get js/window :PokiSDK)
    (j/call-in js/window [:PokiSDK :gameplayStop])))

(defn commercial-break []
  (when (j/get js/window :PokiSDK)
    (-> (j/call-in js/window [:PokiSDK :commercialBreak])
        (.then #(gameplay-start)))))

(defn rewarded-break []
  (when (j/get js/window :PokiSDK)
    (-> (j/call-in js/window [:PokiSDK :rewardedBreak])
        (.then #(if %
                  (fire :rewarded-break-potions)
                  (do
                    (println "No reward due to ad blocker!")
                    (fire :ui-show-adblock-warning-text)))))))

(on :rewarded-break rewarded-break)

(comment
  (init)
  )
