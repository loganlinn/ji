(ns ji.service
  (:require [ji.service.lobby :as lobby]
            [ji.service.game :as game-view]
            [ji.domain.game :as game :refer [new-game game-over?]]
            [ji.domain.messages :as msg]
            [ji.util.async :refer [map-source map-sink]]
            [com.keminglabs.jetty7-websockets-async.core :as ws]
            [clojure.edn :as edn]
            [clojure.core.async :refer [chan go <! >! <!! >!! alt! alts! put! close!]]
            [clojure.core.match :refer [match]]
            [hiccup.core :refer [html]]
            [hiccup.page :as page]
            [compojure.core :refer [routes GET]]
            [compojure.route :as route])
  (:import [ji.domain.messages GameJoinMessage PlayerSetMessage]))

(defrecord GameEnv [id game clients join-chan])

(defn create-app [game-envs]
  (routes
    (GET "/games" []
         (page/html5
           (page/include-css "stylesheets/app.css")
           (lobby/render-lobby @game-envs)))
    (GET "/games/:game-id" [game-id]
         (if-let [game-env (get @game-envs game-id)]
           (page/html5
             (page/include-css "/stylesheets/app.css")
             (game-view/render-game @game-env))
           (route/not-found "Game not found")))
    (GET "/" [] {:status 302 :headers {"Location" "/index.html"} :body ""})
    (route/files "/" {:root "out/public"})
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

(defn broadcast-msg!
  [msg clients]
  (doseq [{c :out} clients]
    (put! c msg)))

(defn broadcast-game!
  [game clients]
  (broadcast-msg! (msg/game-state :game game) clients)
  game)


(defprotocol IGameMessage
  (apply-message [_ game-env]))

(extend-protocol IGameMessage
  PlayerSetMessage
  (apply-message [{:keys [cards player-id]} {:keys [game clients]}]
    (if (game/valid-set? game cards)
      (game/take-set game player-id cards)
      (game/revoke-set game player-id))))

(defn- separate-client
  "Returns [client other-clients] by identifying client by input channel"
  [clients client-in]
  (let [m (group-by #(= (:in %) client-in) clients)]
    [(first (m true)) (m false)]))

(defn- exit-game!
  [{:keys [game clients]}]
  (broadcast-msg! (msg/map->GameFinishMessage {:game game}) clients)
  game)

(defn go-game
  [game-env]
  (go (loop []
        (let [{:keys [game clients join-chan]} @game-env]
          (if (game-over? game)
            (exit-game! @game-env)
            (match (alts! (cons join-chan (map :in clients)))
                   [nil join-chan] (exit-game! @game-env)

                   ;; Player Join
                   [msg join-chan]
                   (let [{:keys [player-id client]} msg
                         clients' (conj clients client)]
                     (do (swap! game-env assoc
                                :game (-> (game/add-player game player-id)
                                          (step-game)
                                          (broadcast-game! clients'))
                                :clients clients')
                         (recur)))

                   ;; Player Disconnect
                   [nil sc]
                   (let [[client other-clients] (separate-client clients sc)]
                     (do (swap! game-env
                                assoc
                                :game (-> game
                                          (game/disconnect-player (:player-id client))
                                          (step-game)
                                          (broadcast-game! other-clients))
                                :clients other-clients)
                         (recur)))

                   ;; GameMessage
                   [(msg :guard #(satisfies? IGameMessage %)) sc]
                   (do (swap! game-env assoc
                              :game (-> (apply-message msg @game-env)
                                        (step-game)
                                        (broadcast-game! clients)))
                       (recur))

                   ;; Unknown Input
                   :else (recur)))))))

(defn init-game-env!
  [game-envs game-id]
  (let [game (new-game)
        join-chan (chan)
        game-env (atom (map->GameEnv {:id game-id
                                      :game game
                                      :clients []
                                      :join-chan join-chan}))
        game-chan (go-game game-env)]
    (swap! game-envs assoc game-id game-env)
    (go (<! game-chan) (swap! game-envs dissoc game-id))
    game-env))

(defn get-game-env!
  "Gets or inits new game"
  [game-envs game-id]
  (let [env @(dosync (or (get @game-envs game-id)
                        (init-game-env! game-envs game-id)))]
    env))

(defn join-game!
  [game-envs game-id join-msg client]
  (let [game-env (get-game-env! game-envs game-id)]
    (>!! (:join-chan game-env) (assoc join-msg :client client))))

(defn client-join
  [game-envs {:keys [uri in out] :as client}]
  (go
    (when-let [join-msg (<! in)]
      (if (and (instance? GameJoinMessage join-msg) (msg/valid? join-msg))
        (let [player-id (:player-id join-msg)
              assoc-player-id #(if (associative? %) (assoc % :player-id player-id) %)
              player-in (map-source assoc-player-id in)
              client {:in player-in :out out :player-id player-id}
              game-id (or (->> uri (re-find #"/game/(\d+)") second) "1")]
          (join-game! game-envs game-id join-msg client))
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
