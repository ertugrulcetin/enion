(ns enion-backend.handler
  (:require
    [enion-backend.env :refer [defaults]]
    [enion-backend.middleware :as middleware]
    [enion-backend.routes.asas]
    [enion-backend.routes.home :refer [home-routes]]
    [enion-backend.routes.mage]
    [enion-backend.routes.party]
    [enion-backend.routes.priest]
    [enion-backend.routes.warrior]
    [mount.core :as mount]
    [reitit.ring :as ring]))

(mount/defstate init-app
                :start ((or (:init defaults) (fn [])))
                :stop  ((or (:stop defaults) (fn []))))

(mount/defstate app-routes
                :start
                (ring/ring-handler
                  (ring/router
                    [(home-routes)])))

(defn app []
  (middleware/wrap-base #'app-routes))
