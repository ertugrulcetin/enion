(ns enion-cljs.scene.lod-manager
  (:require
    [applied-science.js-interop :as j]
    [clojure.set :as set]
    [common.enion.npc :as common.npc]
    [enion-cljs.common :refer [on]]
    [enion-cljs.scene.pc :as pc :refer [app]]
    [enion-cljs.scene.states :as st]
    [goog.functions :as functions]))

;; TODO ranges for other player
;; x < 2 -> LOD 0
;; 2 < X < 5 -> LOD 1
;; 5 < X < 35 -> LOD 2
;; X < 35 -> Disable
;; X < 20 -> Run animations

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

;; TODO remove memo, replace get-in with m/get!
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

(let [lod-selection {:lod-0 #{:lod-1 :lod-2}
                     :lod-1 #{:lod-0 :lod-2}
                     :lod-2 #{:lod-0 :lod-1}}]
  (defn- set-lod [lod player-id]
    (j/assoc-in! st/other-players [player-id lod :enabled] true)
    (doseq [lod-to-disable (lod-selection lod)]
      (j/assoc-in! st/other-players [player-id lod-to-disable :enabled] false))))

(let [lods #{:lod-0 :lod-1 :lod-2}]
  (defn- disable-player [player-id]
    (doseq [l lods]
      (j/assoc-in! st/other-players [player-id l :enabled] false))
    (j/assoc-in! st/other-players [player-id :armature :enabled] false)))

(defn- enable-player [player-id]
  (j/assoc-in! st/other-players [player-id :armature :enabled] true))

(defn- enable-username [player-id]
  (when-not (and (st/enemy? player-id) (j/get (st/get-other-player player-id) :hide?))
    (j/assoc-in! st/other-players [player-id :char-name :enabled] true)))

(defn- disable-username [player-id]
  (j/assoc-in! st/other-players [player-id :char-name :enabled] false))

(def distances #js {})
(def lod-0-threshold 1.5)
(def lod-1-threshold 5)
(def lod-2-threshold 25)
(def animation-on-threshold 20)

(defonce visible-player-ids (js/Set.))
(defonce non-visible-player-ids (js/Set.))

(defn- process-players [world-layer]
  (j/call visible-player-ids :clear)
  (j/call non-visible-player-ids :clear)
  (doseq [mesh-instances (j/get world-layer :transparentMeshInstances)]
    (when-let [player-id (and (j/get mesh-instances :visibleThisFrame)
                              (j/get-in mesh-instances [:node :player_id]))]
      (j/call visible-player-ids :add player-id))
    (when-let [player-id (and (not (j/get mesh-instances :visibleThisFrame))
                              (j/get-in mesh-instances [:node :player_id]))]
      (j/call non-visible-player-ids :add player-id)))
  (doseq [id (js/Object.keys st/other-players)
          :let [distance (st/distance-to-player id)]]
    (when-not (= (j/get distances id) distance)
      (j/assoc! distances id distance)
      (let [username-distance (cond
                                (st/enemy? id) 10
                                (st/party-member? id) 20
                                :else 5)]
        (cond
          (< distance lod-0-threshold) (set-lod :lod-0 id)
          (< distance lod-1-threshold) (set-lod :lod-1 id)
          (< distance lod-2-threshold) (set-lod :lod-2 id)
          :else (disable-player id))
        (when (< distance lod-2-threshold)
          (enable-player id))
        (if (<= distance username-distance)
          (enable-username id)
          (disable-username id))))
    (cond
      (j/call non-visible-player-ids :has id)
      (j/assoc-in! st/other-players [id :anim-component :enabled] false)

      (and (j/call visible-player-ids :has id)
           (<= distance animation-on-threshold))
      (j/assoc-in! st/other-players [id :anim-component :enabled] true)

      :else
      (j/assoc-in! st/other-players [id :anim-component :enabled] false))))

(def npc-distances #js {})

(defonce visible-npc-ids (js/Set.))
(defonce non-visible-npc-ids (js/Set.))

(let [lod-selection {:lod-0 #{:lod-1 :lod-2}
                     :lod-1 #{:lod-0 :lod-2}
                     :lod-2 #{:lod-0 :lod-1}}]
  (defn- set-npc-lod [lod npc-id]
    (j/assoc-in! st/npcs [npc-id lod :enabled] true)
    (doseq [lod-to-disable (lod-selection lod)]
      (j/assoc-in! st/npcs [npc-id lod-to-disable :enabled] false))))

(let [lods #{:lod-0 :lod-1 :lod-2}]
  (defn- disable-npc [npc-id]
    (doseq [l lods]
      (j/assoc-in! st/npcs [npc-id l :enabled] false))
    (j/assoc-in! st/npcs [npc-id :armature :enabled] false)))

(defn- enable-npc [npc-id]
  (j/assoc-in! st/npcs [npc-id :armature :enabled] true))

(defn- enable-npc-username [npc-id]
  (j/assoc-in! st/npcs [npc-id :char-name :enabled] true))

(defn- disable-npc-username [npc-id]
  (j/assoc-in! st/npcs [npc-id :char-name :enabled] false))

(comment
  (j/get-in st/npcs [id :anim-component :enabled])
  )

(let [lod-0-threshold 1.5
      lod-1-threshold 5
      lod-2-threshold common.npc/lod-2-threshold
      animation-on-threshold (- lod-2-threshold 2)]
  (defn- process-npcs [world-layer]
    (j/call visible-npc-ids :clear)
    (j/call non-visible-npc-ids :clear)
    (doseq [mesh-instances (j/get world-layer :opaqueMeshInstances)]
      (when-let [npc-id (and (j/get mesh-instances :visibleThisFrame)
                             (j/get-in mesh-instances [:node :npc_id]))]
        (j/call visible-npc-ids :add npc-id))
      (when-let [npc-id (and (not (j/get mesh-instances :visibleThisFrame))
                             (j/get-in mesh-instances [:node :npc_id]))]
        (j/call non-visible-npc-ids :add npc-id)))
    (doseq [id (js/Object.keys st/npcs)
            :let [distance (st/distance-to-player id true)]]
      (when-not (= (j/get npc-distances id) distance)
        (j/assoc! npc-distances id distance)
        (let [username-distance 5]
          (cond
            (< distance lod-0-threshold) (set-npc-lod :lod-0 id)
            (< distance lod-1-threshold) (set-npc-lod :lod-1 id)
            (< distance lod-2-threshold) (set-npc-lod :lod-2 id)
            :else (disable-npc id))
          (when (< distance lod-2-threshold)
            (enable-npc id))
          (if (<= distance username-distance)
            (enable-npc-username id)
            (disable-npc-username id))))
      (cond
        (j/call non-visible-npc-ids :has id)
        (j/assoc-in! st/npcs [id :anim-component :enabled] false)

        (and (j/call visible-npc-ids :has id)
             (<= distance animation-on-threshold))
        (j/assoc-in! st/npcs [id :anim-component :enabled] true)

        :else
        (j/assoc-in! st/npcs [id :anim-component :enabled] false)))))

(let [find-fn (fn [t] (lod-keys t))]
  (defn- process-mesh-instance [mesh-instance]
    (when ^boolean (j/get mesh-instance :visibleThisFrame)
      (let [entity (.-node mesh-instance)
            tags (.list ^js/Object (.-tags entity))]
        (when (> (count tags) 0)
          (when-let [player-entity (st/get-player-entity)]
            (when-let [entity-type (some find-fn tags)]
              (let [lod-0-range (get-lod-range entity-type "lod-0")
                    lod-1-range (get-lod-range entity-type "lod-1")
                    lod-2-range (get-lod-range entity-type "lod-2")
                    parent-guid (pc/get-guid (.-parent entity))
                    lod-0 (parent->entity-attr parent-guid "lod-0")
                    lod-1 (parent->entity-attr parent-guid "lod-1")
                    lod-2 (parent->entity-attr parent-guid "lod-2")
                    distance (pc/distance (pc/get-pos player-entity)
                                          (parent->entity-attr parent-guid "position"))]
                (cond
                  (< distance (inc lod-0-range))
                  (do
                    (j/assoc! lod-0 :enabled true)
                    (j/assoc! lod-1 :enabled false)
                    (j/assoc! lod-2 :enabled false))

                  (< lod-0-range distance (inc lod-1-range))
                  (do
                    (j/assoc! lod-0 :enabled false)
                    (j/assoc! lod-1 :enabled true)
                    (j/assoc! lod-2 :enabled false))

                  (< lod-1-range distance (inc lod-2-range))
                  (do
                    (j/assoc! lod-0 :enabled false)
                    (j/assoc! lod-1 :enabled false)
                    (j/assoc! lod-2 :enabled true)))))))))))

(defn- process-on-post-cull []
  (let [world-layer (j/call-in app [:scene :layers :getLayerByName] "World")]
    (j/assoc! world-layer :onPostCull (functions/throttle
                                        (fn [_]
                                          (.forEach (j/get world-layer :opaqueMeshInstances) process-mesh-instance)
                                          (process-players world-layer)
                                          (process-npcs world-layer))
                                        500))))

(comment
  (let [world-layer (j/call-in app [:scene :layers :getLayerByName] "World")]
    (js/console.log (j/get world-layer :opaqueMeshInstances)))
  )

(defn- check-distances-map []
  (js/setInterval
    (fn []
      (when (> (count (js/Object.keys st/other-players)) 100)
        (set! distances #js {})))
    60000))

(defn init []
  (println "LOD manager starting...")
  (create-template-indexes)
  (process-on-post-cull)
  (check-distances-map))

(on :start-lod-manager #(init))

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
