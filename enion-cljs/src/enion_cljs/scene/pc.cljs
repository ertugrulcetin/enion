(ns enion-cljs.scene.pc
  (:require
    [applied-science.js-interop :as j]
    [camel-snake-kebab.core :as csk]
    [clojure.string :as str]))

(defonce app nil)

(def key->code
  (->> (js->clj js/pc :keywordize-keys true)
       (filter #(or (str/starts-with? (name (first %)) "KEY_")
                    (str/starts-with? (name (first %)) "EVENT_")
                    (str/starts-with? (name (first %)) "MOUSEBUTTON_")))
       (into {})))

(def find-by-name
  (memoize
    (fn [name]
      (j/call-in app [:root :findByName] name))))

(defn vec3
  ([]
   (vec3 0 0 0))
  ([x y z]
   (js/pc.Vec3. x y z)))

(defn addv [^js/pc.Vec3 v1 ^js/pc.Vec3 v2]
  (.add v1 v2))

(defn copyv [^js/pc.Vec3 v1 ^js/pc.Vec3 v2]
  (.copy v1 v2))

(defn mul-scalar [^js/pc.Vec3 v s]
  (.mulScalar v s))

(defn normalize [^js/pc.Vec3 v]
  (.normalize v))

(defn scale [^js/pc.Vec3 v s]
  (.scale v s))

(defn setv [^js/pc.Vec3 v x y z]
  (.set v x y z))

(defn set-pos [^js/pc.Entity e ^js/pc.Vec3 v]
  (.setPosition e v))

(defn set-loc-pos
  ([^js/pc.Entity e ^js/pc.Vec3 v]
   (.setLocalPosition e v))
  ([^js/pc.Entity e x y z]
   (.setLocalPosition e x y z)))

(defn set-euler [^js/pc.Entity e ^js/pc.Vec3 v]
  (.setEulerAngles e v))

(defn get-loc-euler [^js/pc.Entity e]
  (.getLocalEulerAngles e))

(defn get-pos [^js/pc.Entity e]
  (.getPosition e))

(defn get-loc-pos [^js/pc.Entity e]
  (.getLocalPosition e))

(defn raycast-first [^js/pc.Vec3 from ^js/pc.Vec3 to]
  (.raycastFirst ^js/pc.RigidBodyComponent (.-systems.rigidbody app) from to))

(defn look-at [^js/pc.Entity e ^js/pc.Vec3 v]
  (.lookAt e v))

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

(defn disable-context-menu []
  (.disableContextMenu ^js/pc.Mouse (.-mouse app)))

(defn mouse-on [key f]
  (.on ^js/pc.Mouse (.-mouse app) (key key->code) f))
