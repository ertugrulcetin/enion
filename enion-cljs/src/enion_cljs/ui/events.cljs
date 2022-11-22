(ns enion-cljs.ui.events
  (:require
    [day8.re-frame.tracing :refer-macros [fn-traced]]
    [enion-cljs.ui.db :as db]
    [re-frame.core :as re-frame]))

(re-frame/reg-event-db
  ::initialize-db
  (fn-traced [_ _]
             db/default-db))
