(ns enion-cljs.scene.pc
  (:require
    ["/enion_cljs/vendor/tween"]
    [applied-science.js-interop :as j]
    [camel-snake-kebab.core :as csk]
    [clojure.string :as str]))

(defonce app nil)

(def map-size 100)
(def map-half-size (/ map-size 2))

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

(defn find-by-tag [tag]
  (j/call-in app [:root :findByTag] tag))

(defn vec3
  ([]
   (vec3 0 0 0))
  ([x y z]
   (js/pc.Vec3. x y z)))

(defn addv [v1 v2]
  (j/call v1 :add v2))

(defn copyv [v1 v2]
  (j/call v1 :copy v2))

(defn mul-scalar [v s]
  (j/call v :mulScalar s))

(defn normalize [v]
  (j/call v :normalize))

(defn scale [v s]
  (j/call v :scale s))

(defn setv [v x y z]
  (j/call v :set x y z))

(defn sub [v1 v2]
  (j/call v1 :sub v2))

(defn distance [v1 v2]
  (j/call v1 :distance v2))

(defn set-loc-pos
  ([e v]
   (j/call e :setLocalPosition v))
  ([e x y z]
   (j/call e :setLocalPosition x y z)))

(defn set-euler
  ([e v]
   (j/call e :setEulerAngles v))
  ([e x y z]
   (j/call e :setEulerAngles x y z)))

(defn get-loc-euler [e]
  (j/call e :getLocalEulerAngles))

(defn get-euler [e]
  (j/call e :getEulerAngles))

(defn set-loc-euler [e x y z]
  (j/call e :setLocalEulerAngles x y z))

(defn get-pos [e]
  (j/call e :getPosition))

(defn set-pos [e v]
  (j/call e :setPosition v))

(defn get-loc-pos [e]
  (j/call e :getLocalPosition))

(defn get-loc-scale [e]
  (j/call e :getLocalScale))

(defn set-loc-scale
  ([e s]
   (j/call e :setLocalScale s s s))
  ([e x y z]
   (j/call e :setLocalScale x y z)))

(defn raycast-first [from to]
  (j/call-in app [:systems :rigidbody :raycastFirst] from to))

(defn look-at
  ([e v]
   (j/call e :lookAt v))
  ([e x y z]
   (j/call e :lookAt x y z))
  ([e x y z reverse?]
   (j/call e :lookAt x y z)
   (when reverse?
     (j/call e :rotateLocal 0 180 0))))

(defn apply-impulse [entity x y z]
  (j/call-in entity [:rigidbody :applyImpulse] x y z))

(defn apply-force [entity x y z]
  (j/call-in entity [:rigidbody :applyForce] x y z))

(defn get-guid [entity]
  (j/call entity :getGuid))

(defn create-script [script-name {:keys [attrs init update post-init post-update]}]
  (let [script (j/call js/pc :createScript (csk/->camelCaseString script-name))]
    (doseq [[k v] attrs]
      (j/call-in script [:attributes :add] (name k) (clj->js v)))
    (some->> init (j/assoc-in! script [:prototype :initialize]))
    (some->> update (j/assoc-in! script [:prototype :update]))
    (some->> post-init (j/assoc-in! script [:prototype :postInitialize]))
    (some->> post-update (j/assoc-in! script [:prototype :postUpdate]))))

(defn pressed? [key]
  (j/call-in app [:keyboard :isPressed] (key key->code)))

(defn disable-context-menu []
  (j/call-in app [:mouse :disableContextMenu]))

(defn on-mouse [key f]
  (j/call-in app [:mouse :on] (key key->code) f))

(defn on-keyboard [key f]
  (j/call-in app [:keyboard :on] (key key->code) f))

(defn key? [e k]
  (= (j/get e :key) (k key->code)))

(defn button? [e k]
  (= (j/get e :button) (k key->code)))

(defn get-code [k]
  (k key->code))

(defn off-anim [entity event-name]
  (j/call-in entity [:anim :off] event-name))

(defn on-anim [entity event-name f]
  (off-anim entity event-name)
  (j/call-in entity [:anim :on] event-name f))

(defn set-anim-boolean [entity param v]
  (j/call-in entity [:anim :setBoolean] param v))

(defn set-anim-int [entity param v]
  (j/call-in entity [:anim :setInteger] param v))

(defn get-anim-state [entity]
  (j/get-in entity [:anim :layers 0 :activeState]))

(defn get-active-state-current-time [entity]
  (j/get-in entity [:anim :layers 0 :activeStateCurrentTime]))

(defn get-active-state-duration [entity]
  (j/get-in entity [:anim :layers 0 :activeStateDuration]))

(defn get-active-state-progress [entity]
  (j/get-in entity [:anim :layers 0 :activeStateProgress]))

(defn reset-anim [e]
  (j/call-in e [:anim :layers 0 :reset]))

(defn play-anim [e]
  (j/call-in e [:anim :layers 0 :play]))

(defn screen-to-world [camera x y]
  (j/call camera :screenToWorld x y (j/get camera :farClip)))

(let [temp (vec3)]
  (defn get-map-pos [e]
    (let [pos (get-pos e)]
      (setv temp (+ (j/get pos :x) map-half-size)
            (j/get pos :y)
            (+ (j/get pos :z) map-half-size)))))

(defn color [r g b]
  (js/pc.Color. r g b))

(defn find-asset-by-name [name]
  (j/call-in app [:assets :_assets :find] #(= name (j/get % :name))))
