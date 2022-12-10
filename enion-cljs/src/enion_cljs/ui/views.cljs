(ns enion-cljs.ui.views
  (:require
    [applied-science.js-interop :as j]
    [breaking-point.core :as bp]
    [enion-cljs.ui.styles :as styles]
    [enion-cljs.ui.subs :as subs]
    [re-frame.core :as re-frame]))

(comment
  (do
    (let [r (rand-int 100)
         s (str r "%")
         hp (js/document.getElementById "mp")
         hit (js/document.getElementById "mp-used")]
     (set! (.-style.width hp) s)
     (set! (.-style.width hit) s))

   (let [r (rand-int 100)
         s (str r "%")
         hp (js/document.getElementById "hp")
         hit (js/document.getElementById "hit")]
     (set! (.-style.width hp) s)
     (set! (.-style.width hit) s)))
  )

;; TODO when game is ready then show HUD
(defn main-panel []
  (let [name (re-frame/subscribe [::subs/name])]
    [:div (styles/ui-panel)
     [:h1 "Hello from " @name]
     [:div (styles/actions-container)

      [:div (styles/hp-mp-container)
       [:div (styles/hp-bar)
        [:div#hit (styles/hp-hit)]
        [:div#hp (styles/hp)]
        [:span (styles/hp-mp-text) "100/100"]]

       [:div (styles/mp-bar)
        [:div#mp-used (styles/mp-used)]
        [:div#mp (styles/mp)]
        [:span (styles/hp-mp-text) "100/100"]]]


      [:div (styles/skill-bar)
       [:div {:class (styles/skill)}
        [:span (styles/skill-number) "1"]
        [:img {:class (styles/skill-img)
               :src "http://localhost:8280/img/nova.jpeg"}]]

       [:div {:class (styles/skill)}
        [:img {:class (styles/skill-img)
               :src "https://hordes.io/assets/ui/skills/14.webp?v=5699699"}]]
       [:div {:class (styles/skill)}
        [:img {:class (styles/skill-img)
               :src "https://hordes.io/assets/ui/skills/14.webp?v=5699699"}]]
       [:div {:class (styles/skill)}
        [:img {:class (styles/skill-img)
               :src "https://hordes.io/assets/ui/skills/14.webp?v=5699699"}]]
       [:div {:class (styles/skill)}
        [:img {:class (styles/skill-img)
               :src "https://hordes.io/assets/ui/skills/14.webp?v=5699699"}]]
       [:div {:class (styles/skill)}
        [:img {:class (styles/skill-img)
               :src "https://hordes.io/assets/ui/skills/14.webp?v=5699699"}]]
       [:div {:class (styles/skill)}
        [:img {:class (styles/skill-img)
               :src "https://hordes.io/assets/ui/skills/14.webp?v=5699699"}]]
       [:div {:class (styles/skill)}
        [:img {:class (styles/skill-img)
               :src "https://hordes.io/assets/ui/skills/14.webp?v=5699699"}]]]]]))
