(ns ji.service.game
  (:require [ji.service.game-env :as game-env]
            [ji.domain
             [game :as g]
             [messages :as msg]]
            [taoensso.timbre :refer [debugf info]]
            [clojure.core.async :refer [chan go go-loop <! >! <!! >!! alt! alts! put! close! map> map<]]
            [clojure.core.match :refer [match]])
  (:import [ji.domain.messages PlayerSetMessage]))

(defn broadcast-msg!
  "Sends message to all clients"
  [msg clients]
  (doseq [{c :out} clients]
    (put! c msg)))

(defn broadcast-game-env!
  "Sends current state of game environment to all clients. Returning game env."
  [{:keys [game clients] :as game-env}]
  (when (seq clients)
    (broadcast-msg! (msg/game-state :game game) clients))
  game-env)

(defn finish-game!
  "Sends message to clients indicating end of game, and performs any necessary
  cleanup to game environment. Returns a game environment"
  [{:keys [game clients] :as game-env}]
  (broadcast-msg! (msg/map->GameFinishMessage {:game game}) clients)
  game-env)

(defn client-in->client-out
  "Returns the output channel for client given their input channel"
  [ch clients]
  (when-let [client (some #(when (= (:in %) ch) %) clients)]
    (:out client)))

(defn go-game
  "Main event loop for a game. Accepts a reference to a game environment"
  [game-env]
  (go-loop []
    (let [{:keys [join-chan] :as env} @game-env]
      (if (g/game-over? (:game env))
        (finish-game! env)
        (let [[msg sc] (alts! (cons join-chan (map :in (:clients env))))]
          (match [msg sc]
            ;; Shutdown
            [nil join-chan] (finish-game! env)

            ;; Player Join
            [msg join-chan]
            (let [{:keys [client player-id]} msg]
              (broadcast-game-env!
                (swap! game-env game-env/connect-client client player-id))
              (recur))

            ;; Player Disconnect
            [nil sc]
            (do (broadcast-game-env!
                  (swap! game-env game-env/disconnect-client sc))
                (recur))

            ;; Heartbeat
            [:ping sc]
            (do
              (when-let [out-ch (client-in->client-out sc (:clients env))]
                (put! out-ch :pong))
              (recur))

            ;; Game Message
            [(msg :guard game-env/game-msg?) sc]
            (do (broadcast-game-env!
                  (swap! game-env game-env/apply-game-message msg))
                (recur))

            ;; Unknown Input
            :else (recur)))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Game Message Implementations
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(extend-protocol game-env/IGameMessage
  PlayerSetMessage
  (apply-message [{:keys [cards player-id]} {:keys [game clients] :as game-env}]
    (->> (if (g/valid-set? game cards)
           (g/take-set game player-id cards)
           (g/revoke-set game player-id))
         (assoc game-env :game))))
