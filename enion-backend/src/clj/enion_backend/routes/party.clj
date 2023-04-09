(ns enion-backend.routes.party
  (:require
    [common.enion.skills :as common.skills]
    [enion-backend.routes.home :refer :all]
    [enion-backend.utils :as utils]))

(defn- asked-to-join-too-often? [id selected-player-id]
  (let [last-time (get-in @players [id :last-time :add-to-party-request selected-player-id])]
    (and (not (nil? last-time))
         (<= (- (now) last-time) common.skills/party-request-duration-in-milli-secs))))

(defn- party-full? [player players]
  (let [party-id (:party-id player)]
    (and party-id
         (= (count (filter #(= party-id (:party-id %)) (vals players))) max-number-of-party-members))))

(defn- already-in-another-party? [player other-player]
  (and (:party-id other-player)
       (not= (:party-id player) (:party-id other-player))))

(defn- blocked-party-requests? [other-player]
  (:party-requests-blocked? other-player))

(defmethod process-party-request :add-to-party [{:keys [id ping] {:keys [selected-player-id]} :data}]
  (let [players* @players]
    (when-let [player (get players* id)]
      (when-let [other-player (get players* selected-player-id)]
        (cond
          (ping-too-high? ping) ping-high
          (= id selected-player-id) party-request-failed
          (not (ally? id selected-player-id)) party-request-failed
          (party-full? player players*) party-request-failed
          (already-in-the-party? player other-player) party-request-failed
          (already-in-another-party? player other-player) party-request-failed
          ;; TODO implement later
          (blocked-party-requests? other-player) party-request-failed
          (asked-to-join-too-often? id selected-player-id) party-request-failed
          :else (do
                  (swap! players assoc-in [id :last-time :add-to-party-request selected-player-id] (now))
                  (send! selected-player-id :party {:type :party-request
                                                    :player-id id})
                  {:type :add-to-party
                   :selected-player-id selected-player-id}))))))

(defmethod process-party-request :remove-from-party [{:keys [id ping] {:keys [selected-player-id]} :data}]
  (let [players* @players]
    (when-let [player (get players* id)]
      (when-let [other-player (get players* selected-player-id)]
        (cond
          (ping-too-high? ping) ping-high
          (not (leader? player)) party-request-failed
          (not (already-in-the-party? player other-player)) party-request-failed
          :else (remove-from-party {:players* players*
                                    :player player
                                    :id id
                                    :selected-player-id selected-player-id}))))))

(defmethod process-party-request :exit-from-party [{:keys [id ping]}]
  (let [players* @players]
    (when-let [player (get players* id)]
      (cond
        (ping-too-high? ping) ping-high
        (not (:party-id player)) party-request-failed
        :else (remove-from-party {:id id
                                  :players* players*
                                  :exit? true})))))

(defn accepts-or-rejects-party-request-on-time? [other-player id]
  (let [last-time (get-in other-player [:last-time :add-to-party-request id])]
    (and last-time (<= (- (now) last-time) common.skills/party-request-duration-in-milli-secs))))

(defmethod process-party-request :accept-party-request [{:keys [id ping] {:keys [requested-player-id]} :data}]
  (let [players* @players]
    (when-let [player (get players* id)]
      (when-let [other-player (get players* requested-player-id)]
        (cond
          (ping-too-high? ping) ping-high
          (not (accepts-or-rejects-party-request-on-time? other-player id)) party-request-failed
          (party-full? other-player players*) party-request-failed
          (already-in-another-party? other-player player) party-request-failed
          (already-in-the-party? other-player player) party-request-failed
          (blocked-party-requests? other-player) party-request-failed
          :else (let [party-id (or (:party-id other-player) (swap! utils/party-id-generator inc))]
                  (swap! players (fn [players]
                                   (-> players
                                       (assoc-in [requested-player-id :party-id] party-id)
                                       (assoc-in [requested-player-id :party-leader?] true)
                                       (assoc-in [id :party-id] party-id)
                                       (assoc-in [id :last-time :accepted-party] (now)))))

                  (send! requested-player-id :party {:type :accepted-party-request
                                                     :player-id id})
                  (doseq [[player-id attrs] @players
                          :when (and (not= id player-id)
                                     (not= requested-player-id player-id)
                                     (= party-id (:party-id attrs)))]
                    (send! player-id :party {:type :joined-party
                                             :player-id id}))
                  {:type :accept-party-request
                   :party-members-ids (cons requested-player-id
                                            (->> @players
                                                 (filter (fn [[_ attrs]]
                                                           (and (= party-id (:party-id attrs)) (not (leader? attrs)))))
                                                 (sort-by (comp :accepted-party :last-time second))
                                                 (map first)))}))))))

(defmethod process-party-request :reject-party-request [{:keys [id ping] {:keys [requested-player-id]} :data}]
  (let [players* @players]
    (when (get players* id)
      (when-let [other-player (get players* requested-player-id)]
        (cond
          (ping-too-high? ping) ping-high
          (not (accepts-or-rejects-party-request-on-time? other-player id)) party-request-failed
          :else (do
                  (send! requested-player-id :party {:type :party-request-rejected
                                                     :player-id id})
                  {:type :reject-party-request
                   :requested-player-id requested-player-id}))))))
