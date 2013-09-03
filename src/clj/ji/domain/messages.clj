(ns ^:shared ji.domain.messages)

(defprotocol IMessage
  (valid? [this]))

(defrecord ErrorMessage [message]
  IMessage
  (valid? [_] true))

(def error ->ErrorMessage)
(defn error? [msg] (instance? ErrorMessage msg))

(defrecord GameStateMessage [game]
  IMessage
  (valid? [_] true))

(defrecord GameJoinMessage [player-id color]
  IMessage
  (valid? [_]
    (and (string? player-id)
         (re-find #"^\w{1,16}$" player-id))))

(defn join-game [& {:as fields}]
  (map->GameJoinMessage fields))

(defrecord GameLeaveMessage [player-id]
  IMessage
  (valid? [_] true))

(defrecord GameControlMessage []
  IMessage
  (valid? [_] true))

(defrecord PlayerSetMessage [cards]
  IMessage
  (valid? [_] (= (count cards) 3)))

(defn game-state [& {:as fields}]
  (let [game (:game fields)]
    (map->GameStateMessage
      (assoc fields :game
             (-> game
                 (assoc :cards-remaining (count (:deck game)))
                 (dissoc :deck)
                 (dissoc :offline-players))))))

;(extend-protocol IMessage
  ;nil
  ;(valid? [_] false)
  ;Object
  ;(valid? [_] false))
