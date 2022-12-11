(ns enion-cljs.ui.styles
  (:require
    [spade.core :refer [defglobal defclass defattrs]]))

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
   :grid-auto-rows "56px"
   :grid-auto-columns "56px"
   :grid-auto-flow :column
   :pointer-events :all})

(defclass skill []
  {:position :relative
   :border-radius "3px"
   :border "3px solid #293c40"
   :cursor :pointer})

(defclass skill-img []
  {:position :absolute
   :width "50px"
   :height "50px"
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

