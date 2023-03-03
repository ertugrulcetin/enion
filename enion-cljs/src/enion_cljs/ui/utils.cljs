(ns enion-cljs.ui.utils
  (:require
    ["bad-words" :as bad-words]
    [clojure.string :as str]))

(def bad-words-filter (new bad-words))

(defn- clean [text]
  (when-not (str/blank? text)
    (.clean bad-words-filter text)))

