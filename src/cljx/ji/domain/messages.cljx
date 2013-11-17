(ns ^:shared ji.domain.messages
  (:require [ji.domain.game :as game]
            [schema.core :as s]
            #+clj [schema.macros :as sm])
  #+cljs (:require-macros [schema.macros :as sm]))

#+cljs (reset! sm/*use-potemkin* false)

(defn valid? [& _] true) ;; TODO
;(defprotocol IMessage
  ;(valid? [this]))

;(defrecord ErrorMessage [message]
  ;IMessage
  ;(valid? [_] true))

;(defrecord GameStateMessage [game]
  ;IMessage
  ;(valid? [_] true))

;(defrecord GameFinishMessage [game]
  ;IMessage
  ;(valid? [_] true))

;(defrecord GameJoinMessage [player-id color]
  ;IMessage
  ;(valid? [_]
    ;(and (string? player-id)
         ;(re-find #"^\w{1,16}$" player-id))))

;(defrecord GameLeaveMessage [player-id]
  ;IMessage
  ;(valid? [_] true))

;(defrecord PlayerSetMessage [cards]
  ;IMessage
  ;(valid? [_] (= (count cards) 3)))

(def +player-id+ #"^\w{1,16}$")

(sm/defrecord ErrorMessage
  [message :- s/String])

(sm/defrecord GameStateMessage
  [game :- s/Any #_game/Game])

(sm/defrecord GameFinishMessage
  [game :- s/Any #_game/Game])

(sm/defrecord GameJoinMessage
  [player-id :- +player-id+
   color :- s/Keyword])

(sm/defrecord GameLeaveMessage
  [player-id :- s/String])

(sm/defrecord PlayerSetMessage
  [cards :- (s/pred #(= 3 (count %)) #(every? map? %)) #_[(s/one {}) (s/one {}) (s/one {})]])

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
