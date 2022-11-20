(ns enion-cljs.views
  (:require
   [re-frame.core :as re-frame]
   [breaking-point.core :as bp]
   [enion-cljs.styles :as styles]
   [enion-cljs.subs :as subs]
   ))

(defn main-panel []
  (let [name (re-frame/subscribe [::subs/name])]
    [:div
     [:h1
      {:class (styles/level1)}
      "Hello from " @name]
     [:div
      [:h3 (str "screen-width: " @(re-frame/subscribe [::bp/screen-width]))]
      [:h3 (str "screen: " @(re-frame/subscribe [::bp/screen]))]]
     ]))
