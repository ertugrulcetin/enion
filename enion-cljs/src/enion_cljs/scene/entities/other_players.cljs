(ns enion-cljs.scene.entities.other-players
  (:require
    [enion-cljs.scene.pc :as pc]
    [enion-cljs.scene.skills.effects :as skills.effects])
  (:require-macros
    [enion-cljs.scene.macros :refer [fnt]]))

(defonce players #js {})

(defn- init-fn [_]
  (skills.effects/register-other-players players))

(defn init []
  (pc/create-script :other-players
                    {:init (fnt (init-fn this))}))

(comment
  (add-player-id-to-healed 111)
  (remove-player-id-from-healed 12)

  (js/console.log healed-player-ids)
  )
