(ns enion-cljs.scene.pc
  (:require
   [applied-science.js-interop :as j]
   [camel-snake-kebab.core :as csk]
   [clojure.string :as str]))

(defonce app nil)

(def key->code
  (->> (js->clj js/pc :keywordize-keys true)
    (filter #(str/starts-with? (name (first %)) "KEY_"))
    (into {})))

(defn vec3
  ([]
   (vec3 0 0 0))
  ([x y z]
   (js/pc.Vec3. x y z)))

(def find-by-name
  (memoize
    (fn [name]
      (j/call-in app [:root :findByName] name))))

(defn create-script [script-name {:keys [attrs init update post-init post-update]}]
  (let [script (j/call js/pc :createScript (csk/->camelCaseString script-name))]
    (doseq [[k v] attrs]
      (j/call-in script [:attributes :add] (name k) (clj->js v)))
    (some->> init (j/assoc-in! script [:prototype :initialize]))
    (some->> update (j/assoc-in! script [:prototype :update]))
    (some->> post-init (j/assoc-in! script [:prototype :postInitialize]))
    (some->> post-update (j/assoc-in! script [:prototype :postUpdate]))))

(defn pressed? [key]
  (.isPressed ^js/pc.Keyboard (.-keyboard app) (key key->code)))
