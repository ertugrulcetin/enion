(ns enion-backend.npc.slot)

(defn- distance [point1 point2]
  (let [dx (- (first point1) (first point2))
        dy (- (second point1) (second point2))]
    (Math/sqrt (+ (* dx dx) (* dy dy)))))

(defn- random-angle []
  (* 2 Math/PI (rand)))

(defn- random-point-in-circle [origin radius]
  (let [angle (random-angle)
        dist (* radius (Math/sqrt (rand)))
        x (+ (first origin) (* dist (Math/cos angle)))
        y (+ (second origin) (* dist (Math/sin angle)))]
    [x y]))

(defn- generate-positions [origin radius num-points min-distance]
  (->> (loop [points []]
         (if (< (count points) num-points)
           (let [point (random-point-in-circle origin radius)]
             (if (some #(<= (distance % point) min-distance) points)
               (recur points)
               (recur (conj points point))))
           points))
       (map-indexed vector)
       (into {})))

(def slot-positions
  {:orc-squid-right-1 (generate-positions [26.55 -33.29] 2 10 1)
   :orc-squid-right-2 (generate-positions [24.24 -40.34] 2 10 1)
   :orc-squid-left-1 (generate-positions [32.83 -27.373] 2 10 1)
   :orc-squid-left-2 (generate-positions [41.01 -25.792] 2 10 1)
   :orc-ghoul-left-1 (generate-positions [37.25 -20.0968] 2.5 10 1)
   :orc-ghoul-left-2 (generate-positions [46.61 -29.02] 2.5 10 1)
   :orc-ghoul-right-1 (generate-positions [24.65 -46.55] 2.5 10 1)
   :orc-skeleton-warrior-right-1 (generate-positions [17.44 -28.06] 2.5 10 1)
   :orc-skeleton-warrior-left-1 (generate-positions [29.05 -16.1] 2.5 10 1)
   :human-squid-left-1 (generate-positions [-35.94 23.998] 2 10 1)
   :human-squid-left-2 (generate-positions [-44.92 23.9453] 2 10 1)
   :human-squid-right-1 (generate-positions [-31.526 29.9057] 2 10 1)
   :human-squid-right-2 (generate-positions [-24.49 36.5530] 2.5 10 1)
   :human-ghoul-left-1 (generate-positions [-39.59 18.97] 2.5 10 1)
   :human-ghoul-right-1 (generate-positions [-23.31 28.79] 2.5 10 1)
   :human-ghoul-right-2 (generate-positions [-25.09 44.48] 2.5 10 1)
   :human-skeleton-warrior-left-1 (generate-positions [-28.69 17.24] 2.5 10 1)
   :human-skeleton-warrior-left-2 (generate-positions [-44.73 8.65] 2.5 10 1)
   :orc-demon (generate-positions [44.85 -20.86] 4 20 1.2)
   :human-demon (generate-positions [-18.58 37.38] 4 20 1.2)
   :gravestalker-1 (generate-positions [7.16 2.96] 6 20 1.2)
   :gravestalker-2 (generate-positions [-7.7 -7.06] 6 20 1.2)
   :skeleton-champion-portal (generate-positions [26.8 38.34] 4.5 10 1)
   :deruvish-1 (generate-positions [-28.17 -28.46] 3 10 1)
   :deruvish-2 (generate-positions [-44.45 -39.63] 3.5 12 1)
   :deruvish-forest (generate-positions [41.2 21.21] 3.5 12 1)
   :orc-burning-skeleton-forest-1 (generate-positions [35.95 -4.81] 3 12 1)
   :orc-burning-skeleton-forest-2 (generate-positions [43.9 10.14] 3 12 1)
   :orc-burning-skeleton-forest-3 (generate-positions [29.01 20.75] 3 12 1)})

(def slots
  [{:type :squid :slot-id :orc-squid-right-1 :count 3}]
  #_[{:type :squid :slot-id :orc-squid-right-1 :count 5}
   {:type :squid :slot-id :orc-squid-right-2 :count 5}
   {:type :squid :slot-id :orc-squid-left-1 :count 5}
   {:type :squid :slot-id :orc-squid-left-2 :count 5}
   {:type :ghoul :slot-id :orc-ghoul-left-1 :count 5}
   {:type :ghoul :slot-id :orc-ghoul-left-2 :count 5}
   {:type :ghoul :slot-id :orc-ghoul-right-1 :count 5}
   {:type :skeleton-warrior :slot-id :orc-skeleton-warrior-right-1 :count 5}
   {:type :skeleton-warrior :slot-id :orc-skeleton-warrior-left-1 :count 5}
   {:type :squid :slot-id :human-squid-left-1 :count 5}
   {:type :squid :slot-id :human-squid-left-2 :count 5}
   {:type :squid :slot-id :human-squid-right-1 :count 5}
   {:type :squid :slot-id :human-squid-right-2 :count 5}
   {:type :ghoul :slot-id :human-ghoul-left-1 :count 5}
   {:type :ghoul :slot-id :human-ghoul-right-1 :count 5}
   {:type :ghoul :slot-id :human-ghoul-right-2 :count 5}
   {:type :skeleton-warrior :slot-id :human-skeleton-warrior-left-1 :count 5}
   {:type :skeleton-warrior :slot-id :human-skeleton-warrior-left-2 :count 5}
   {:type :skeleton-champion :slot-id :skeleton-champion-portal :count 5}
   {:type :demon :slot-id :orc-demon :count 5}
   {:type :demon :slot-id :human-demon :count 5}
   {:type :gravestalker :slot-id :gravestalker-1 :count 6}
   {:type :gravestalker :slot-id :gravestalker-2 :count 6}
   {:type :deruvish :slot-id :deruvish-1 :count 5}
   {:type :deruvish :slot-id :deruvish-2 :count 5}
   {:type :deruvish :slot-id :deruvish-forest :count 5}
   {:type :burning-skeleton :slot-id :orc-burning-skeleton-forest-1 :count 5}
   {:type :burning-skeleton :slot-id :orc-burning-skeleton-forest-2 :count 5}
   {:type :burning-skeleton :slot-id :orc-burning-skeleton-forest-3 :count 5}])
