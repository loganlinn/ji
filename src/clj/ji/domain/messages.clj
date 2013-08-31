(ns ji.domain.messages)

(defprotocol IMessage
  (valid? [this]))

(defrecord ErrorMessage [message]
  IMessage
  (valid? [_] true))

(defrecord GameStateMessage [game]
  IMessage
  (valid? [_] true))

(defrecord GameJoinMessage [player-id]
  IMessage
  (valid? [_] (not (clojure.string/blank? player-id))))

(defrecord GameLeaveMessage [player-id]
  IMessage
  (valid? [_] true))

(defrecord GameControlMessage []
  IMessage
  (valid? [_] true))

(defrecord PlayerSetMessage [cards]
  IMessage
  (valid? [_] true))

;(extend-protocol IMessage
  ;nil
  ;(valid? [_] false)
  ;Object
  ;(valid? [_] false))
