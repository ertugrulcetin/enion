(ns enion-backend.middleware
  (:require
    [clojure.tools.logging :as log]
    [enion-backend.config :refer [env]]
    [enion-backend.env :refer [defaults]]
    [enion-backend.layout :refer [error-page]]
    [enion-backend.middleware.formats :as formats]
    [muuntaja.middleware :refer [wrap-format wrap-params]]
    [ring-ttl-session.core :refer [ttl-memory-store]]
    [ring.middleware.anti-forgery :refer [wrap-anti-forgery]]
    [ring.middleware.defaults :refer [site-defaults wrap-defaults]]))

(defn wrap-internal-error
  [handler]
  (let [error-result (fn [^Throwable t]
                       (log/error t (.getMessage t))
                       (error-page {:status 500
                                    :title "Something very bad has happened!"
                                    :message "We've dispatched a team of highly trained gnomes to take care of the problem."}))]
    (fn wrap-internal-error-fn
      ([req respond _]
       (handler req respond #(respond (error-result %))))
      ([req]
       (try
         (handler req)
         (catch Throwable t
           (error-result t)))))))

(defn wrap-csrf
  [handler]
  (wrap-anti-forgery
    handler
    {:error-response
     (error-page
       {:status 403
        :title "Invalid anti-forgery token"})}))

(defn wrap-formats
  [handler]
  (let [wrapped (-> handler wrap-params (wrap-format formats/instance))]
    (fn
      ([request]
       ;; disable wrap-formats for websockets
       ;; since they're not compatible with this middleware
       ((if (:websocket? request) handler wrapped) request))
      ([request respond raise]
       ((if (:websocket? request) handler wrapped) request respond raise)))))

(defn wrap-base
  [handler]
  (-> ((:middleware defaults) handler)
      (wrap-defaults
        (-> site-defaults
            (assoc-in [:security :anti-forgery] false)
            (assoc-in  [:session :store] (ttl-memory-store (* 60 30)))))
      wrap-internal-error))

