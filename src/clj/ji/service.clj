(ns ji.service
  (:require [ji.domain.game :as game :refer [new-game game-over?]]
            [ji.domain.messages :as msg]
            [ji.util.async :refer [map-source map-sink]]
            [com.keminglabs.jetty7-websockets-async.core :as ws]
            [clojure.edn :as edn]
            [clojure.core.async :refer [chan go <! >! <!! >!! alt! alts! put! close!]]
            [clojure.core.match :refer [match]]
            [compojure.core :refer [routes]]
            [compojure.route :as route])
  (:import [ji.domain.messages GameJoinMessage PlayerSetMessage]))

(def app
  (routes
    (route/files "/" {:root "public"})))

(def data-readers
  {'ji.domain.messages.ErrorMessage #'msg/map->ErrorMessage
   'ji.domain.messages.GameJoinMessage #'msg/map->GameJoinMessage
   'ji.domain.messages.GameLeaveMessage #'msg/map->GameLeaveMessage
   'ji.domain.message.GameStateMessage #'msg/map->GameStateMessage
   'ji.domain.messages.GameControlMessage #'msg/map->GameControlMessage
   'ji.domain.messages.PlayerSetMessage #'msg/map->PlayerSetMessage})

(defn client-read-string [data]
  (edn/read-string {:readers data-readers} data))

(defn refill-board
  "Returns game after filling board to 12 cards"
  [{:keys [board deck] :as game}]
  (let [num-add (- 12 (count board))]
    (if (pos? num-add)
      (game/draw-cards num-add game)
      game)))

(defn fix-setless-board [game]
  (loop [game game]
    (if (empty? (game/solve-board (:board game)))
      (recur (game/draw-cards 3 game))
      game)))

(defn step-game
  [game]
  (-> game
      (refill-board)
      (fix-setless-board)))

(defn broadcast-game!
  [game clients]
  (let [msg (msg/game-state :game game)]
    (doseq [{c :out} clients] ;; todo loop/alts! ?
      (put! c msg)))
  game)

(defprotocol IGameMessage
  (apply-message [_ game]))

(extend-protocol IGameMessage
  PlayerSetMessage
  (apply-message [{:keys [cards player-id]} game]
    (if (game/valid-set? game cards)
      (game/take-set game player-id cards)
      (game/revoke-set game player-id))))

(defn- separate-client
  "Returns [client other-clients] by identifying client by input channel"
  [clients client-in]
  (let [m (group-by #(= (:in %) client-in) clients)]
    [(m true) (m false)]))

(defn go-game
  [game join-msgs]
  (go
    (loop [game game
           clients []]
      (println "------------------------------------------")
      (if-not (game-over? game)
        (let [[msg sc] (alts! (cons join-msgs (map :in clients)))]
          (if (= sc join-msgs)
            (let [{:keys [player-id client]} msg
                  clients' (conj clients client)]
              (println "JOIN MSG" (count clients) msg)
              (recur (-> (game/add-player game player-id)
                         (step-game)
                         (broadcast-game! clients'))
                     clients'))
            (do (println "GAME MSG" msg sc)
                (cond
                  (nil? msg) ;; disconnect player
                  (let [[client other-clients] (separate-client clients sc)]
                    (recur (-> game
                               (game/disconnect-player (:player-id client))
                               (step-game)
                               (broadcast-game! other-clients))
                           other-clients))

                  (satisfies? IGameMessage msg) ;; TODO validate
                  (recur (-> (apply-message msg game)
                             (step-game)
                             (broadcast-game! clients))
                         clients)
                  :else
                  (do (println "UNHANDLED" msg)
                      (recur game clients))))
            ))
        {:game game :clients clients}))))

(defn init-game-env!
  [game-envs game-id]
  (let [game (new-game)
        join-chan (chan)
        game-chan (go-game game join-chan)
        m {:game-chan game-chan
           :join-chan join-chan}]
    (swap! game-envs assoc game-id m)
    m))

(defn get-game-env!
  "Gets or inits new game"
  [game-envs game-id]
  (dosync (or (@game-envs game-id)
              (init-game-env! game-envs game-id))))

(defn join-game!
  [game-envs game-id join-msg]
  (let [game-env (get-game-env! game-envs game-id)]
    (>!! (:join-chan game-env) join-msg)))

(defn client-join
  [game-envs {:keys [uri in out] :as client}]
  (go
    (when-let [join-msg (<! in)]
      (if (and (instance? GameJoinMessage join-msg) (msg/valid? join-msg))
        (let [player-id (:player-id join-msg)
              assoc-player-id #(if (associative? %) (assoc % :player-id player-id) %)
              player-in (map-source assoc-player-id in)
              client {:in player-in :out out :player-id player-id}]
          (join-game! game-envs uri (assoc join-msg :client client)))
        (do
          (>! out (msg/->ErrorMessage "You're strange"))
          (close! out))))))

(defn wrap-client-chan
  "Reverse the in/out for our sanity, and communicate via edn"
  [{:keys [in out] :as client}]
  (assoc client
         :in (map-source client-read-string out)
         :out (map-sink pr-str in)))

(defn register-ws-app!
  [game-envs client-chan]
  (go (while true
        (->> (<! client-chan)
             (wrap-client-chan)
             (client-join game-envs)))))

(comment
  (match [conn]
         [{:uri uri :in in :out out}]
         (go
           (>! in (str "Yo, " uri))
           (loop []
             (when-let [msg (<! out)]
               (prn msg)
               (recur))))))
