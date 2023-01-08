(ns enion-backend.env
  (:require
    [selmer.parser :as parser]
    [clojure.tools.logging :as log]
    [enion-backend.dev-middleware :refer [wrap-dev]]))

(def defaults
  {:init
   (fn []
     (parser/cache-off!)
     (log/info "\n-=[enion-backend started successfully using the development profile]=-"))
   :stop
   (fn []
     (log/info "\n-=[enion-backend has shut down successfully]=-"))
   :middleware wrap-dev})
