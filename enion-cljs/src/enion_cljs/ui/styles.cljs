(ns enion-cljs.ui.styles
  (:require
    [spade.core :refer [defglobal defclass defattrs defkeyframes]]))

#_(sr/inject!
  "enion-cljs-ui-styles-cooldown-property"
  "@property --cooldown {\n  syntax: \"<percentage>\";\n  inherits: false;\n  initial-value: 0%;\n}")

(defglobal defaults
  [:body
   {:height "100%"
    :width "100%"
    :background-color :white}])

(defattrs ui-panel []
  {:position :absolute
   :top 0
   :left 0
   :height "100%"
   :width "100%"
   :user-select :none})

(defattrs actions-container []
  {:position :absolute
   :transform "translateX(-50%)"
   :left "50%"
   :bottom "20px"
   :z-index 3})

(defattrs hp-mp-container []
  {:position :relative
   :margin "0 auto 4px auto"
   :box-shadow "0px 0px 0px 2px #ffffff00"
   :padding "3px"
   :background-color "#10131ca3"
   :border-radius "3px"})

(defattrs hp-bar []
  {:z-index 0
   :background-color "rgba(45, 66, 71, 0.7)"
   :border-radius "1.5px"
   :position :relative
   :color "#DAE8EA"
   :overflow :hidden
   :white-space :nowrap
   :text-transform :capitalize
   :font-weight :bold
   :text-align "center"})

(defattrs mp-bar []
  {:composes [(hp-bar)]
   :margin-top "4px"})

(defattrs hp []
  {:position :absolute
   :width "100%"
   :font-size "1.3em"
   :border-radius "1.5px"
   :height "100%"
   :background "linear-gradient(0deg, #c0403f 0%, #af1a1b 49%, #c0403f 50%)"
   :transition "width .5s linear"})

(defattrs mp []
  {:composes [(hp)]
   :background "linear-gradient(0deg, #2a4fdf 0%, #1b27bd 49%, #2a4fdf 50%)"})

(defattrs hp-hit []
  {:position :absolute
   :background "#c0403f9b"
   :width "100%"
   :height "100%"
   :transition "width .8s linear"})

(defattrs mp-used []
  {:composes [(hp-hit)]
   :background "#2a4fdf94"})

(defattrs hp-mp-text []
  {:position :sticky
   :padding-left "4px"
   :user-select :none})

(defattrs skill-bar []
  {:background-color "#10131ca3"
   :padding "2px"
   :border "2px solid #10131ca3"
   :border-radius "5px"
   :display :grid
   :grid-gap "3px"
   :grid-auto-rows "46px"
   :grid-auto-columns "46px"
   :grid-auto-flow :column
   :pointer-events :all})

(defclass skill []
  {:position :relative
   :border-radius "3px"
   :border "3px solid #293c40"
   :cursor :pointer})

(defclass skill-img []
  {:position :absolute
   :width "40px"
   :height "40px"
   :pointer-events :none})

(defattrs skill-number []
  {:color "#5b858e"
   :pointer-events :none
   :position :absolute
   :line-height "8px"
   :font-size "14px"
   :background-color "rgba(16, 19, 29, 0.8)"
   :padding "2px 1px 3px 1px"
   :border-radius "2px"
   :z-index 10
   :font-weight :bold})

(defclass chat-wrapper []
  {:position :absolute
   :width "30%"
   :left "10px"
   :margin-left :auto
   :height :auto
   :bottom "45px"
   :z-index 15
   :opacity 0.75})

(defattrs info-box-wrapper []
  {:composes [(chat-wrapper)]
   :width "25%"
   :right 0
   :left :unset
   :bottom "10px"})

(defattrs chat []
  {:overflow :hidden
   :width "100%"
   :border-radius "5px"
   :text-align :left
   :font-size "13px"
   :font-weight "bold"
   :color "white"
   :margin-right "10px"
   :right "0px"
   :bottom "50px"
   :overflow-y :auto
   :padding "10px"
   ;; :height "180px"
   :pointer-events :all
   :left "10px"
   ;; For Firefox
   :-ms-overflow-style :none
   :scrollbar-width :none
   :z-index 10}
  ["&::-webkit-scrollbar" {:display :none}])

(defattrs info-box []
  {:composes [(chat)]
   :left :unset
   :bottom "20px"
   :height "150px"})

(defclass message-box []
  {:width "100%"
   :height "120px"
   :line-height "1.65em"
   :overflow-y :auto
   ;; For Firefox
   :-ms-overflow-style :none
   :scrollbar-width :none}
  ["&::-webkit-scrollbar" {:display :none}]
  [:strong {:padding "2px"
            :background-color "#10131dcc"
            :border-radius "3px"}]
  [:span {:padding "2px"
          :margin-left "5px"
          :line-height "1.6rem"
          :background-color "#10131dcc"
          :border-radius "3px"}])

(defclass info-message-box []
  {:composes [(message-box)]
   :height "150px"}
  [:span {:margin-left :unset}
   [:&.damage {:color "#ff0000ff"}]
   [:&.hit {:color "#e1dedeff"}]
   [:&.bp {:color "#5f5fcbff"}]
   [:&.skill {:color "#e1dedeff"}]
   [:&.using-potion {:color "#01ffbbd3"}]
   [:&.hp-recover {:color "#53b226ff"}]
   [:&.mp-recover {:color "#2691b2ff"}]
   [:&.skill-failed {:color "#ffc301c8"}]]
  ["&::-webkit-scrollbar" {:display :block}])

(defattrs chat-part-message-box []
  {:color "rgb(15 188 3)"})

(defclass chat-input []
  {:width "345px"
   :height "28px"
   :background-color "#10131dcc"
   :outline :none
   :color :white
   :font-weight :bold
   :font-size "14px"
   :font-family "Comic Papyrus"
   :border "2px solid #10131dcc"
   :border-radius "2px"})

(defclass button []
  {:outline :none
   :font-size "16px"
   :font-family "Comic Papyrus"
   :background-color "#10131dcc"
   :border "2px solid #10131dcc"
   :border-radius "5px"
   :color "white"
   :cursor :pointer
   :pointer-events :all
   :user-select :none}
  [:&:hover
   {:border "2px solid grey"
    :border-radius "5px"}])

(defattrs selected-player []
  {:composes [(actions-container)]
   :top "20px"
   :bottom :unset
   :text-align :center
   :pointer-events :none})

(defattrs selected-player-text []
  {:font-size "30px"
   :color "#b62c2b"
   :text-shadow "-1px -1px 0 #000, 1px -1px 0 #000, -1px 1px 0 #000, 1px 1px 0 #000;"})

(defattrs hp-bar-selected-player []
  {:composes [(hp-bar)]
   :width "350px"
   :height "20px"
   :border "2px solid #10131dcc"
   :border-radius "3px"})

(defclass info-close-button []
  {:composes [(button)]
   :margin-left "10px"
   :font-size "14px"
   :border-radius "5px"})

(defclass info-open-button []
  {:composes [(info-close-button)]
   :position :absolute
   :opacity 0.5
   :right "20px"
   :bottom "10px"})

(defclass chat-close-button []
  {:composes [(info-close-button)]})

(defclass chat-open-button []
  {:composes [(chat-close-button)]
   :position :fixed
   :bottom "20px"})

(defclass chat-all-button []
  {:composes [(button)]
   :position :fixed
   :margin-top "5px"})

(defclass chat-party-button []
  {:composes [(chat-all-button)]
   :margin-left "40px"
   :color "rgb(15 188 3) !important"})

(defclass chat-party-button-selected []
  {:border "2px solid rgb(15 188 3)"}
  [:&:hover {:border "2px solid rgb(15 188 3)"}])

(defclass chat-all-button-selected []
  {:border "2px solid white"}
  [:&:hover {:border "2px solid white"}])

(defattrs minimap []
  {:position :absolute
   :right "10px"
   :top "10px"
   :width "148px"
   :height "148px"
   :z-index 3
   :border "2px solid black"
   :border-radius "3px"
   :user-select :none
   :pointer-events :none})

(defattrs map-overflow []
  {:position :absolute
   :overflow :hidden
   :width "100%"
   :height "100%"
   :left 0
   :top 0
   :background "#78b9cf"})

(defclass holder []
  {:position :absolute
   :width "500px"
   :height "500px"
   :transition "left .3s linear, top .3s linear"})

(defclass minimap-img []
  {:position :absolute
   :width "100%"
   :height "100%"
   :left 0
   :top 0
   :z-index 1})

(defattrs minimap-player []
  {:background-size "100% 100% !important"
   :width "12px"
   :height "12px"
   :z-index 1
   :position :absolute
   :left "50%"
   :top "50%"
   :transform "translate(-50%, -50%)"
   :background "url(\"http://localhost:8280/img/pointer.png\") center 0px no-repeat"})

(defkeyframes cooldown-frames []
  ["0%" {:--cooldown "0%"}]
  ["100%" {:--cooldown "100%"}])

(defattrs cooldown [secs]
  {:--coldown "50%"
   :background "conic-gradient(transparent var(--cooldown), rgba(0, 0, 0, 0.5) var(--cooldown))"
   :animation [[(cooldown-frames) (str secs "s linear infinite")]]})

(defattrs childs-overlayed []
  {:height "40px"
   :width "40px"
   :border "2px solid black"}
  [:&>*
   {:position :absolute
    :height "100%"
    :width "100%"
    :z-index 9}])
