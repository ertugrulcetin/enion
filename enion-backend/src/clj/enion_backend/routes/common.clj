(ns enion-backend.routes.common)

(defmulti apply-skill (fn [{:keys [data]}]
                        (:skill data)))
