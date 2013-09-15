(ns ji.service.game-env
  (:require [ji.domain.game :as game]
            [ji.domain.messages :as msg])
  (:import [ji.domain.messages PlayerSetMessage]))

(defrecord GameEnv [id game clients join-chan])

(defprotocol IGameMessage
  (apply-message [_ game-env]))

(defn game-msg? [m] (satisfies? IGameMessage m))

(extend-protocol IGameMessage
  PlayerSetMessage
  (apply-message [{:keys [cards player-id]} {:keys [game clients]}]
    (if (game/valid-set? game cards)
      (game/take-set game player-id cards)
      (game/revoke-set game player-id))))


(defn fill-board
  "Returns game after filling board to 12 cards"
  [{:keys [board deck] :as game}]
  (let [num-add (- 12 (count board))]
    (if (pos? num-add)
      (game/draw-cards num-add game)
      game)))

(defn fix-setless-board
  "If game's board contains sets, returns game as-is, otherwise, adds 3 cards
  until at least 1 set exists on board"
  [game]
  (loop [game game]
    (if (empty? (game/solve-board (:board game)))
      (recur (game/draw-cards 3 game))
      game)))

(defn step-game
  [game]
  (-> game (fill-board) (fix-setless-board)))

(defn create-game-env [game-id game join-chan]
  (map->GameEnv {:game-id game-id
                 :game game
                 :clients []
                 :join-chan join-chan}))
