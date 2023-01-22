(ns enion-cljs.scene.macros
  (:require
    [applied-science.js-interop :as j]))

(def wasd-codes #{"KeyW" "KeyA" "KeyS" "KeyD"})

(defmacro fnt [& body]
  `(fn [~'dt]
     (cljs.core/this-as ~'this ~@body)))

(defmacro process-cancellable-skills [skills key-code active-state state]
  `(when (~wasd-codes ~key-code)
     (j/assoc! ~state :target-pos-available? false)
     (enion-cljs.scene.pc/set-locater-target)
     (cond
       ~@(apply concat
                (for [s skills]
                  `[(enion-cljs.scene.skills.core/can-skill-be-cancelled? ~s ~active-state ~state)
                    (enion-cljs.scene.skills.core/cancel-skill ~s)])))))
