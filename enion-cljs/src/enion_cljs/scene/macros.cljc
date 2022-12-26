(ns enion-cljs.scene.macros
  (:require
    [applied-science.js-interop :as j]))

(defmacro fnt [& body]
  `(fn [~'dt]
     (cljs.core/this-as ~'this ~@body)))

(defmacro process-cancellable-skills [skills active-state state]
  `(when (enion-cljs.scene.keyboard/pressing-wasd?)
     (j/assoc! ~state :target-pos-available? false)
     (cond
       ~@(apply concat
                (for [s skills]
                  `[(enion-cljs.scene.animations.core/skill-cancelled? ~s ~active-state ~state)
                    (enion-cljs.scene.animations.core/cancel-skill ~s)])))))
