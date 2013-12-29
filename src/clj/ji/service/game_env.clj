(ns ji.service.game-env
  (:require [ji.domain.game :as game]
            [ji.domain.messages :as msg]
            [ji.util :as util :refer [now]]))

(def default-max-clients 9)

(defrecord GameEnv [id game clients join-chan max-clients])

(defprotocol IGameMessage
  (apply-message [_ game-env]))

(defn game-msg? [m] (satisfies? IGameMessage m))

(defn- separate-client
  "Returns [client other-clients] by identifying client by input channel"
  [clients client-in]
  (let [m (group-by #(= (:in %) client-in) clients)]
    [(first (m true)) (m false)]))

(defn step-game-env
  [game-env]
  (update-in game-env [:game] game/update-board))

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

(defn max-clients? [game-env]
  (>= (count (:clients game-env))
      (:max-clients game-env)))

(defn create-game-env [game-id game join-chan]
  (map->GameEnv {:id game-id
                 :game game
                 :clients []
                 :max-clients default-max-clients
                 :join-chan join-chan
                 :created-at (now)
                 :updated-at (now)}))
