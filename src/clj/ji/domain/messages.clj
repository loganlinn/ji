(ns ji.domain.messages)

(defprotocol IMessage)

(defrecord ErrorMessage [message]
  IMessage)

(defrecord GameStateMessage [state board players]
  IMessage)

(defrecord JoinGameMessage [player-id]
  IMessage)

(defrecord RequestControlMessage []
  IMessage)

(defrecord SubmitSetMessage [cards]
  IMessage)

;(def data-readers {})
;(defn- add-msg-reader
  ;[readers sym]
  ;(merge readers
         ;{}))
;(defmacro defmessage [& record-args]
  ;`(do
     ;(defrecord ~@record-args)
     ;(alter-var-root data-readers add-msg-reader ~(first record-args))))

