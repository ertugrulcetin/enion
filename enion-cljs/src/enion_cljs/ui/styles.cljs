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

(defclass button []
  {:outline :none
   :font-size "16px"
   :font-family "Comic Papyrus"
   :background-color "#10131dcc"
   :border "2px solid #10131dcc"
   :border-radius "5px"
   :color "white"
   :cursor :unset
   :pointer-events :all
   :user-select :none}
  [:&:hover
   {:border "2px solid grey"
    :border-radius "5px"}])

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

(defattrs hp [health]
  {:position :absolute
   :width (str health "%")
   :font-size "1.3em"
   :border-radius "1.5px"
   :height "100%"
   :background "linear-gradient(0deg, #c0403f 0%, #af1a1b 49%, #c0403f 50%)"
   :transition "width .1s linear"})

(defattrs mp [mana]
  {:composes [(hp mana)]
   :background "linear-gradient(0deg, #2a4fdf 0%, #1b27bd 49%, #2a4fdf 50%)"})

(defattrs hp-hit [health]
  {:position :absolute
   :background "#c0403f9b"
   :width (str health "%")
   :height "100%"
   :transition "width .3s linear"})

(defattrs mp-used [mana]
  {:composes [(hp-hit mana)]
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
   :cursor :unset})

(defclass skill-img [blocked? not-enough-mana]
  (cond-> {:position :absolute
           :width "40px"
           :height "40px"
           :pointer-events :none}
    blocked? (assoc :filter "opacity(0.5)")
    not-enough-mana (assoc :filter "grayscale(1)")))

(defattrs skill-number []
  {:color "#5b858e"
   :pointer-events :none
   :position :absolute
   :line-height "8px"
   :font-size "12px"
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
   :opacity 0.8})

(defattrs info-box-wrapper []
  {:composes [(chat-wrapper)]
   :width "25%"
   :right "25px"
   :left :unset
   :bottom "10px"})

(defattrs chat []
  {:width "100%"
   :border-radius "5px"
   :text-align :left
   :font-size "13px"
   :font-weight "bold"
   :color "white"
   :margin-right "10px"
   :right "0px"
   :bottom "50px"
   :padding "10px"
   ;; :height "180px"
   :pointer-events :all
   :left "10px"
   ;; For Firefox
   :z-index 10})

(defattrs info-box []
  {:composes [(chat)]
   :left :unset
   :bottom "20px"
   :height "150px"})

(def scroll-bar
  ["&::-webkit-scrollbar" {:width "10px"
                           :background-color "rgba(33, 33, 33, 0.5)"
                           :border-radius "5px"}])

(def scroll-bar-thumb
  ["&::-webkit-scrollbar-thumb" {:background-color "black"
                                 :border-radius "5px"}])

(defclass message-box []
  {:width "100%"
   :height "140px"
   :line-height "1.65em"
   ;; For Firefox
   :overflow-y :auto
   :scrollbar-width :none
   :-ms-overflow-style :none}
  scroll-bar
  scroll-bar-thumb
  [:strong {:padding "2px"
            :background-color "#10131dcc"
            :border-radius "3px"}
   [:&.orc-defeats {:color "#ff0000ff"
                    :padding "0px 2px 0px 2px"}]
   [:&.human-defeats {:color "#2691b2ff"
                      :padding "0px 2px 0px 2px"}]]
  [:span {:padding "2px"
          :margin-left "5px"
          :line-height "1.6rem"
          :background-color "#10131dcc"
          :border-radius "3px"}])

(defclass info-message-box []
  {:composes [(message-box)]
   :height "150px"}
  scroll-bar
  scroll-bar-thumb
  [:span {:margin-left :unset}
   [:&.damage {:color "#ff0000ff"}]
   [:&.hit {:color "#e1dedeff"}]
   [:&.bp {:color "#9696e8"}]
   [:&.skill {:color "#e1dedeff"}]
   [:&.hp-recover {:color "#53b226ff"}]
   [:&.mp-recover {:color "#2691b2ff"}]
   [:&.skill-failed {:color "#ffc301c8"}]])

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

(defattrs selected-player []
  {:composes [(actions-container)]
   :top "20px"
   :bottom :unset
   :text-align :center
   :pointer-events :none})

(defattrs selected-player-text [enemy?]
  {:font-size "30px"
   :color (if enemy? "#b62c2b" "white")
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
   :background "url(\"img/pointer.png\") center 0px no-repeat"})

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

(defattrs party-request-modal []
  {:position :absolute
   :top "50%"
   :left "50%"
   :transform "translate(-50%, -50%)"
   :color "white"
   :border-radius "5px"
   :background-color "#10131dcc"
   :padding "20px"
   :border "1px solid black"
   :z-index 1
   :font-size "20px"
   :text-align :center})

(defattrs party-request-buttons-container []
  {:display :flex
   :justify-content :space-between
   :margin-top "20px"})

(defclass party-request-buttons []
  {:composes [(button)]
   :width "100px"
   :height "40px"
   :font-size "18px"
   :border-radius "5px"})

(defclass party-request-accept-button []
  {:composes [(party-request-buttons)]}
  [:&:hover {:color "rgb(15 188 3) !important"
             :border "2px solid rgb(15 188 3)"}])

(defclass party-request-reject-button []
  {:composes [(party-request-buttons)]}
  [:&:hover {:color "#b62c2b !important"
             :border "2px solid #b62c2b"}])

(defattrs party-request-count-down []
  {:text-align :center
   :font-size "20px"
   :margin-top "10px"})

(defattrs re-spawn-modal []
  {:composes [(party-request-modal)]
   :width "30%"})

(defattrs re-spawn-button-container []
  {:display :flex
   :justify-content :center
   :margin-top "20px"})

(defclass re-spawn-button []
  {:composes [(party-request-accept-button)]}
  [:&:disabled {:opacity 0.5
                :cursor :not-allowed
                :color "grey !important"
                :border "2px solid grey"}])

(def party-member-hp-mp-height "12px")

(defattrs party-list-container [minimap-open?]
  {:position :sticky
   :width "200px"
   :height "100%"
   :margin-left :auto
   :z-index 3
   :float :right
   :margin-top (if minimap-open? "170px" "10px")})

(defattrs party-action-button-container []
  {:display :flex
   :flex-direction :row
   :justify-content :end})

(defclass party-action-button []
  {:composes [(button)]
   :font-size "16px"
   :opacity 0.75
   :margin-bottom "5px"
   :margin-right "10px"})

(defclass party-member-hp-mp-container [selected?]
  {:composes [(hp-mp-container)]
   :text-align :center
   :border (if selected? "2px solid rgb(15 188 3)" "2px solid transparent")
   :width "145px"
   :height "35px"
   :margin-right "10px"
   :pointer-events :all}
  [:&:hover {:border "2px solid rgb(15 188 3)"}])

(defattrs party-member-hp-bar []
  {:composes [(hp-bar)]
   :height party-member-hp-mp-height})

(defattrs party-member-mp-bar []
  {:composes [(party-member-hp-bar)]
   :margin-top "4px"})

(defattrs party-member-hp [health]
  {:composes [(hp health)]
   :height party-member-hp-mp-height})

(defattrs party-member-hp-hit [health]
  {:composes [(hp-hit health)]
   :height party-member-hp-mp-height})

(defattrs party-member-username []
  {:color "white"
   :font-size "13px"})

(defattrs init-modal []
  {:composes [(party-request-modal)]
   :width "40%"})

(defclass init-modal-username-input []
  {:color "white"
   :width "300px"
   :height "40px"
   :font-family "Comic Papyrus"
   :border-radius "5px"
   :border "1px solid black"
   :background-color "#10131dcc"
   :outline :none
   :padding "5px"
   :font-size "20px"
   :text-align :center})

(defattrs init-modal-race-container []
  {:display :flex
   :justify-content :center
   :gap "10px"
   :margin-top "20px"})

(defattrs init-modal-class-container []
  {:composes [(init-modal-race-container)]})

(defclass init-modal-button [race selected?]
  {:composes [(re-spawn-button)]}
  [:&:disabled {:opacity 0.5
                :cursor :not-allowed
                :color "grey !important"
                :border "2px solid grey"}]
  (case race
    "orc" (list
            [:&:hover {:color "#ff0000ff !important"
                       :border "2px solid #ff0000ff"}]
            (when selected?
              [:& {:color "#ff0000ff !important"
                   :border "2px solid #ff0000ff"}]))
    "human" (list
              [:&:hover {:color "#2691b2ff !important"
                         :border "2px solid #2691b2ff"}]
              (when selected?
                [:& {:color "#2691b2ff !important"
                     :border "2px solid #2691b2ff"}]))
    (list
      [:& {:color "white"}]
      [:&:hover {:color "white !important"
                 :border "2px solid white"}])))

(defclass init-modal-orc-button [selected?]
  {:composes [(init-modal-button nil nil)]}
  [:&:hover {:color "#ff0000ff !important"
             :border "2px solid #ff0000ff"}]
  (when selected?
    [:& {:color "#ff0000ff !important"
         :border "2px solid #ff0000ff"}]))

(defclass init-modal-human-button [selected?]
  {:composes [(init-modal-button nil nil)]}
  [:&:hover {:color "#2691b2ff !important"
             :border "2px solid #2691b2ff"}]
  (when selected?
    [:& {:color "#2691b2ff !important"
         :border "2px solid #2691b2ff"}]))

(defclass init-modal-enter-button []
  {:composes [(init-modal-button nil nil)]
   :margin-top "20px"
   :font-size "20px"}
  [:&:hover {:color "#ffc301c8 !important"
             :border "2px solid #ffc301c8"}])

(defattrs init-modal-hr []
  {:border-color "grey"})

(defattrs init-modal-error []
  {:color "red"})
