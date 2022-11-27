(ns enion-cljs.scene.lod-manager
  (:require
    [applied-science.js-interop :as j]
    [clojure.set :as set]
    [enion-cljs.scene.entities.player :as entity.player]
    [enion-cljs.scene.pc :as pc :refer [app]]
    [goog.functions :as functions]
    [goog.object :as ob]))

(defonce entities (atom {}))

(def lod-ranges
  {"tree-big" {"lod-0" 15
               "lod-1" 30
               "lod-2" 40}
   "orc-house" {"lod-0" 20
                "lod-1" 40
                "lod-2" 50}
   "orc-mine" {"lod-0" 20
               "lod-1" 40
               "lod-2" 50}
   "orc-luberjack" {"lod-0" 10
                    "lod-1" 15
                    "lod-2" 20}
   "human-barrack" {"lod-0" 20
                    "lod-1" 40
                    "lod-2" 50}
   "human-mine" {"lod-0" 10
                 "lod-1" 15
                 "lod-2" 20}
   "human-smithy" {"lod-0" 20
                   "lod-1" 40
                   "lod-2" 50}})

(def lod-keys (set (keys lod-ranges)))

(def template-tags
  #{"tree-big-template"
    "orc-house-template"
    "orc-mine-template"
    "orc-luberjack-template"
    "human-barrack-template"
    "human-mine-template"
    "human-smithy-template"})

(def get-lod-range
  (memoize
    (fn [entity-type lod]
      (get-in lod-ranges [entity-type lod]))))

(def parent->entity-attr
  (memoize
    (fn [parent-guid attr]
      (get-in @entities [parent-guid attr]))))

;; TODO adjust for dynamic models, e.g. other player's chars
(defn- create-template-indexes []
  (doseq [t template-tags]
    (let [templates (pc/find-by-tag t)]
      (doseq [t templates
              :let [uid (pc/get-guid t)]]
        (swap! entities assoc
               uid
               (->> (.-children t)
                    (map
                      (fn [c]
                        (when-let [tags (seq (.list ^js/Object (.-tags c)))]
                          (let [tags-set (set tags)]
                            (hash-map
                              "position" (pc/get-pos c)
                              (first (set/intersection #{"lod-0" "lod-1" "lod-2"} tags-set)) c)))))
                    (into {})))))))

;; TODO think memo this fn
(let [find-fn (fn [t] (lod-keys t))]
  (defn- process-mesh-instance [mesh-instance]
    (when ^boolean (ob/get mesh-instance "visibleThisFrame")
      (let [entity (.-node mesh-instance)
            tags (.list ^js/Object (.-tags entity))]
        (when (> (count tags) 0)
          (when-let [entity-type (some find-fn tags)]
            (let [lod-0-range (get-lod-range entity-type "lod-0")
                  lod-1-range (get-lod-range entity-type "lod-1")
                  lod-2-range (get-lod-range entity-type "lod-2")
                  parent-guid (pc/get-guid (.-parent entity))
                  lod-0 (parent->entity-attr parent-guid "lod-0")
                  lod-1 (parent->entity-attr parent-guid "lod-1")
                  lod-2 (parent->entity-attr parent-guid "lod-2")
                  distance (pc/distance (pc/get-pos entity.player/entity) (parent->entity-attr parent-guid "position"))]
              (cond
                (< distance (inc lod-0-range))
                (do
                  (set! (.-enabled lod-0) true)
                  (set! (.-enabled lod-1) false)
                  (set! (.-enabled lod-2) false))

                (< lod-0-range distance (inc lod-1-range))
                (do
                  (set! (.-enabled lod-0) false)
                  (set! (.-enabled lod-1) true)
                  (set! (.-enabled lod-2) false))

                (< lod-1-range distance (inc lod-2-range))
                (do
                  (set! (.-enabled lod-0) false)
                  (set! (.-enabled lod-1) false)
                  (set! (.-enabled lod-2) true))))))))))

(defn- process-on-post-cull []
  (let [world-layer (j/call-in app [:scene :layers :getLayerByName] "World")]
    (j/assoc! world-layer :onPostCull (functions/throttle
                                        (fn [_]
                                          (.forEach ^js/Array (.-opaqueMeshInstances world-layer) process-mesh-instance))
                                        500))))

(defn init []
  (create-template-indexes)
  (process-on-post-cull))

(comment
  (count (pc/find-by-tag "tree-big-template"))
  (count @entities)
  (init)
  (create-template-indexes)
  (process-on-post-cull)
  (js/console.log (.-tags (first (pc/find-by-tag "lod-0"))))

  @entities
  (create-template-indexes)
  (set! (.-enabled (get-in @entities ["! {" "lod-0"])) true)

  (js/console.log (.getGuid (.-parent (get-in @entities ["! {" "lod-0"]))))
  )
