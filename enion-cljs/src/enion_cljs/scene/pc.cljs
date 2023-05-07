(ns enion-cljs.scene.pc
  (:require
    [applied-science.js-interop :as j]
    [camel-snake-kebab.core :as csk]
    [clojure.string :as str]
    [common.enion.skills :as common.skills]))

(defonce app nil)

(def map-size 100)
(def map-half-size (/ map-size 2))

(def linear)
(def expo-in)

(def vec3-down js/pc.Vec3.DOWN)

(def key->code
  (->> (js->clj js/pc :keywordize-keys true)
       (filter #(or (str/starts-with? (name (first %)) "KEY_")
                    (str/starts-with? (name (first %)) "EVENT_")
                    (str/starts-with? (name (first %)) "MOUSEBUTTON_")))
       (into {})))

(defn root []
  (j/get app :root))

(defn find-by-name
  ([name]
   (j/call-in app [:root :findByName] name))
  ([entity name]
   (j/call entity :findByName name)))

(defn find-all-by-name [name]
  (j/call-in app [:root :find] (fn [x] (= name (j/get x :name)))))

(defn find-by-tag [tag]
  (j/call-in app [:root :findByTag] tag))

(let [v3 js/pc.Vec3]
  (defn vec3
    ([]
     (vec3 0 0 0))
    ([x y z]
     (v3. x y z))))

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

(defn set-pos
  ([e v]
   (j/call e :setPosition v))
  ([e x y z]
   (j/call e :setPosition x y z)))

(defn get-loc-pos [e]
  (j/call e :getLocalPosition))

(defn get-loc-scale [e]
  (j/call e :getLocalScale))

(defn set-loc-scale
  ([e s]
   (j/call e :setLocalScale s s s))
  ([e x y z]
   (j/call e :setLocalScale x y z)))

(let [q js/pc.Quat]
  (defn quat []
    (q.)))

(defn set-from-euler
  ([q v]
   (j/call q :setFromEulerAngles v))
  ([q x y z]
   (j/call q :setFromEulerAngles x y z)))

(defn slerp [])

(defn raycast-first [from to]
  (j/call-in app [:systems :rigidbody :raycastFirst] from to))

(defn raycast-all [from to]
  (j/call-in app [:systems :rigidbody :raycastAll] from to))

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

(let [create-script* (j/get js/pc :createScript)]
  (defn create-script [script-name {:keys [attrs init update post-init post-update]}]
    (let [script (create-script* (csk/->camelCaseString script-name))]
      (doseq [[k v] attrs]
        (j/call-in script [:attributes :add] (name k) (clj->js v)))
      (some->> init (j/assoc-in! script [:prototype :initialize]))
      (some->> update (j/assoc-in! script [:prototype :update]))
      (some->> post-init (j/assoc-in! script [:prototype :postInitialize]))
      (some->> post-update (j/assoc-in! script [:prototype :postUpdate])))))

(defn pressed? [key]
  (j/call-in app [:keyboard :isPressed] (key key->code)))

(defn disable-context-menu []
  (j/call-in app [:mouse :disableContextMenu]))

(defn on-mouse [key f]
  (j/call-in app [:mouse :on] (key key->code) f))

(defn on-keyboard [key f]
  (j/call-in app [:keyboard :on] (key key->code) f))

(defn off-all-keyboard-events []
  (j/call-in app [:keyboard :off]))

(defn off-all-mouse-events []
  (j/call-in app [:mouse :off]))

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

(defn screen-to-world
  ([camera x y]
   (j/call camera :screenToWorld x y (j/get camera :farClip)))
  ([camera x y ray-dir]
   (j/call camera :screenToWorld x y (j/get camera :farClip) ray-dir)))

(let [temp (vec3)]
  (defn get-map-pos [e]
    (let [pos (get-pos e)]
      (setv temp (+ (j/get pos :x) map-half-size)
            (j/get pos :y)
            (+ (j/get pos :z) map-half-size)))))

(let [clr js/pc.Color]
  (defn color [r g b]
    (clr. r g b)))

(defn find-asset-by-name [name]
  (j/call-in app [:assets :_assets :find] #(= name (j/get % :name))))

(defn add-child [parent child]
  (j/call parent :addChild child))

(defn clone [e]
  (j/call e :clone))

(def terrain-mat (delay (j/get-in (find-by-name "terrain") [:render :meshInstances 0 :material])))

(let [target #js []]
  (defn set-locater-target
    ([]
     (j/call @terrain-mat :setParameter "target_position_available" false))
    ([x z]
     (j/assoc! target 0 x 1 z)
     (j/call @terrain-mat :setParameter "target_position" target)
     (j/call @terrain-mat :setParameter "target_position_available" true))))

(let [target #js []]
  (defn set-selected-player-position
    ([]
     (j/call @terrain-mat :setParameter "selected_char_position_available" false))
    ([x z]
     (j/assoc! target 0 x 1 z)
     (j/call @terrain-mat :setParameter "selected_char_position" target)
     (j/call @terrain-mat :setParameter "selected_char_position_available" true))))

(let [heal-positions #js []
      into-arr (fn [aseq]
                 (j/assoc! heal-positions :length 0)
                 (reduce (fn [a x] (j/call a :push x) a) heal-positions aseq))]
  (defn set-heal-positions
    ([]
     (j/call @terrain-mat :setParameter "heal_position_lengths" 0))
    ([length positions]
     (into-arr positions)
     (j/call @terrain-mat :setParameter "heal_position_lengths" length)
     (j/call @terrain-mat :setParameter "heal_positions[0]" positions))))

(let [ally-color #js [0 1 0]
      enemy-color #js [1 0 0]]
  (defn set-selected-char-color [type]
    (j/call @terrain-mat :setParameter "selected_char_color" (if (= :ally type) ally-color enemy-color))))

(let [target #js []
      nova-pos (vec3)]
  (defn set-nova-circle-pos
    ([]
     (j/call @terrain-mat :setParameter "spell_available" false))
    ([player x y z]
     (j/assoc! target 0 x 1 z)
     (setv nova-pos x y z)
     (j/call @terrain-mat :setParameter "spell_opacity"
             (if (> (distance nova-pos (get-pos (j/get player :entity))) common.skills/attack-range-distance-threshold)
               0.1
               1.0))
     (j/call @terrain-mat :setParameter "spell_position" target)
     (j/call @terrain-mat :setParameter "spell_available" true))))

(defn set-elapsed-time-for-terrain [et]
  (j/call @terrain-mat :setParameter "elapsed_time" et))

(defn update-anim-speed [e clip-name speed]
  (some-> e
          (j/call-in [:anim :layers 0 :_controller :_animEvaluator :findClip] clip-name)
          (j/assoc! :speed speed)))

(let [r js/pc.Ray]
  (defn ray []
    (r.)))

(defn enable [entity]
  (j/assoc! entity :enabled true))

(defn disable [entity]
  (j/assoc! entity :enabled false))

(defn enabled? [entity]
  (true? (j/get entity :enabled)))

(defn disabled? [entity]
  (false? (j/get entity :enabled)))

(defn set-mesh-opacity [entity opacity]
  (when (enabled? entity)
    (j/call-in entity [:render :meshInstances 0 :setParameter] "material_opacity" opacity)))

(defn get-mesh-opacity [entity]
  (j/get-in entity [:render :meshInstances 0 :parameters :material_opacity :data]))

(defn raycast-rigid-body [e camera-entity]
  (let [x (j/get e :x)
        y (j/get e :y)
        camera (j/get camera-entity :camera)
        from (get-pos camera-entity)
        to (screen-to-world camera x y)]
    (raycast-first from to)))

(defn raycast-all-rigid-body [e camera-entity]
  (let [x (j/get e :x)
        y (j/get e :y)
        camera (j/get camera-entity :camera)
        from (get-pos camera-entity)
        to (screen-to-world camera x y)]
    (raycast-all from to)))

(let [clamp* (j/get-in js/pc [:math :clamp])]
  (defn clamp [value min max]
    (clamp* value min max)))
