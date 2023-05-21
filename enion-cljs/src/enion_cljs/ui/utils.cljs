(ns enion-cljs.ui.utils
  (:require
    ["bad-words" :as bad-words]
    [applied-science.js-interop :as j]
    [clojure.string :as str]))

(def bad-words-filter (new bad-words))

(defn clean [text]
  (when-not (str/blank? text)
    (.clean bad-words-filter text)))

(when bad-words-filter
  (.removeWords bad-words-filter "cok" "çok"))

(defn img->img-url [img]
  (str "img/" img))

(defn to-locale [str]
  (some-> str (j/call :toLocaleString "en-US")))
