(ns ji.service.game-env
  (:require [ji.domain.game :as game]
            [ji.domain.messages :as msg]
            [ji.util :as util :refer [now]])
  (:import [ji.domain.messages PlayerSetMessage]))


(defrecord GameEnv [id game clients join-chan])

(defprotocol IGameMessage
  (apply-message [_ game-env]))

(defn game-msg? [m] (satisfies? IGameMessage m))

(extend-protocol IGameMessage
  PlayerSetMessage
  (apply-message [{:keys [cards player-id]} {:keys [game clients] :as game-env}]
    (assoc game-env
           :game (if (game/valid-set? game cards)
                   (game/take-set game player-id cards)
                   (game/revoke-set game player-id)))))

(defn- separate-client
  "Returns [client other-clients] by identifying client by input channel"
  [clients client-in]
  (let [m (group-by #(= (:in %) client-in) clients)]
    [(first (m true)) (m false)]))

(defn fill-board
  "Returns game after filling board to 12 cards"
  ([game] (fill-board game game/default-board-size))
  ([{:keys [board deck] :as game} board-size]
   (let [num-add (min (- board-size (count board)) (count deck))]
     (if (pos? num-add)
       (game/draw-cards num-add game)
       game))))

(defn fix-setless-board
  "If game's board contains sets, returns game as-is, otherwise, adds 3 cards
  until at least 1 set exists on board"
  [game]
  (loop [game game]
    (if (and (seq (:deck game))
             (empty? (game/solve-board (:board game))))
      (recur (game/draw-cards 3 game))
      game)))

(defn step-game
  [game]
  (-> game (fill-board) (fix-setless-board)))

(defn step-game-env
  [game-env]
  (update-in game-env [:game] step-game))

(defn connect-client
  [game-env client player-id]
  (-> game-env
      (update-in [:game] game/add-player player-id)
      (update-in [:clients] conj client)
      (step-game-env)))

(defn disconnect-client
  [{:keys [clients] :as game-env} client-in]
  (let [[client other-clients] (separate-client clients client-in)]
    (-> game-env
        (update-in [:game] game/disconnect-player (:player-id client))
        (assoc :clients other-clients)
        (step-game-env))))

(defn apply-game-message
  [game-env game-msg]
  (-> (apply-message game-msg game-env)
      (step-game-env)))

(defn create-game-env [game-id game join-chan]
  (map->GameEnv {:id game-id
                 :game game
                 :clients []
                 :join-chan join-chan
                 :created-at (now)
                 :updated-at (now)}))
