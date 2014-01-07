(ns ^:shared ji.domain.messages
  (:require [ji.domain.game :as game]))

(defprotocol IMessage
  (valid? [this]))

(defrecord ErrorMessage [message]
  IMessage
  (valid? [_] true))

(defrecord GameStateMessage [game]
  IMessage
  (valid? [_] true))

(defrecord GameFinishMessage [game]
  IMessage
  (valid? [_] true))

(defrecord GameJoinMessage [player-id color]
  IMessage
  (valid? [_]
    (and (string? player-id)
         (re-find #"^\w{1,16}$" player-id))))

(defrecord GameLeaveMessage [player-id]
  IMessage
  (valid? [_] true))

(defrecord PlayerSetMessage [cards]
  IMessage
  (valid? [_] (= (count cards) 3)))

(defn join-game [& {:as fields}]
  (map->GameJoinMessage fields))

(def error ->ErrorMessage)

(defn error? [msg] (instance? ErrorMessage msg))

(defn game-state [& {:as fields}]
  (let [game (:game fields)]
    (map->GameStateMessage
      (assoc fields :game
             (-> game
                 (assoc :cards-remaining (count (:deck game)))
                 (dissoc :deck)
                 (dissoc :offline-players))))))
