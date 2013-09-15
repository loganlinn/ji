(ns ji.service
  (:require [ji.service.templates :as tmpl]
            [ji.service.game-env :as game-env]
            [ji.domain.game :as game :refer [new-game game-over?]]
            [ji.domain.messages :as msg]
            [ji.util.async :refer [map-source map-sink]]
            [com.keminglabs.jetty7-websockets-async.core :as ws]
            [clojure.edn :as edn]
            [clojure.core.async :refer [chan go <! >! <!! >!! alt! alts! put! close!]]
            [clojure.core.match :refer [match]]
            [hiccup.core :refer [html]]
            [hiccup.page :as page]
            [ring.middleware.params :refer [wrap-params]]
            [ring.util.response :as resp]
            [compojure.core :refer [routes GET POST]]
            [compojure.route :as route])
  (:import [ji.domain.messages GameJoinMessage PlayerSetMessage]))

(def data-readers
  {'ji.domain.messages.ErrorMessage #'msg/map->ErrorMessage
   'ji.domain.messages.GameJoinMessage #'msg/map->GameJoinMessage
   'ji.domain.messages.GameLeaveMessage #'msg/map->GameLeaveMessage
   'ji.domain.message.GameStateMessage #'msg/map->GameStateMessage
   'ji.domain.messages.GameControlMessage #'msg/map->GameControlMessage
   'ji.domain.messages.PlayerSetMessage #'msg/map->PlayerSetMessage})

(defn client-read-string [data]
  (edn/read-string {:readers data-readers} data))


(defn broadcast-msg!
  [msg clients]
  (doseq [{c :out} clients]
    (put! c msg)))

(defn broadcast-game-env!
  [{:keys [game clients] :as game-env}]
  (when (seq clients)
    (broadcast-msg! (msg/game-state :game game) clients)))

(defn- exit-game!
  [{:keys [game clients]}]
  (broadcast-msg! (msg/map->GameFinishMessage {:game game}) clients)
  (doseq [client clients] (close! (:out client)))
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
                   (let [{:keys [client player-id]} msg]
                     (do (broadcast-game-env!
                           (swap! game-env game-env/connect-client client player-id))
                         (recur)))

                   ;; Player Disconnect
                   [nil sc]
                   (do (broadcast-game-env!
                         (swap! game-env game-env/disconnect-client sc))
                       (recur))

                   ;; GameMessage
                   [(msg :guard game-env/game-msg?) sc]
                   (do (broadcast-game-env!
                         (swap! game-env game-env/apply-game-message msg))
                       (recur))

                   ;; Unknown Input
                   :else (recur)))))))

(defn init-game-env!
  [game-envs game-id]
  (let [game (new-game)
        join-chan (chan)
        game-env (atom (game-env/create-game-env game-id game join-chan))
        game-chan (go-game game-env)]
    (swap! game-envs assoc game-id game-env)
    (go (let [finshed-game (<! game-chan)]
          (println ("GAME FINISH" finished-game))
          (swap! game-envs dissoc (:id finshed-game))))
    game-env))

(let [cs (vec "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ")]
  (defn rand-game-id [game-envs]
    (let [s (apply str (for [i (range 5)] (rand-nth cs)))]
      (if (contains? @game-envs s)
        (recur game-envs)
        s))))

(defn valid-game-id? [game-id]
  (boolean (re-find #"^[\w-]+$" game-id)))

(defn create-app [game-envs]
  (-> (routes
        (POST "/games" {{game-id "game-id"} :params uri :uri}
              (dosync
                (cond
                  (and game-id (not (valid-game-id? game-id)))
                  (-> (resp/response "Invalid game") (resp/status 400))

                  (and game-id (contains? @game-envs game-id))
                  (-> (resp/response "Game already exists") (resp/status 409))

                  :else
                  (let [game-env (init-game-env! game-envs (or game-id (rand-game-id game-envs)))]
                    (resp/redirect-after-post (str "/games/" (:id @game-env)))))))
        (GET "/games" []
             (tmpl/render-lobby @game-envs))
        (GET "/games/:game-id" [game-id]
             (if-let [game-env (get @game-envs game-id)]
               (tmpl/render-game @game-env)
               (route/not-found (tmpl/render-game-create game-id))))
        (GET "/" [] {:status 302 :headers {"Location" "/games"} :body ""})
        (route/files "/" {:root "out/public"})
        (route/files "/" {:root "public"}))
      (wrap-params)))


(defn join-game!
  [game-envs game-id join-msg client]
  (if-let [game-env (@game-envs game-id)]
    (go (>! (:join-chan @game-env) (assoc join-msg :client client)))
    (go (println "INVALID GAME!!" game-id)
        (>! (:out client) (msg/error "Unknown game"))
        (close! (:out client)))))

(defn client-join
  [game-envs {:keys [uri in out] :as client}]
  (if-let [game-id (second (re-find #"^/games/([\w-]+)" uri))]
    (go
      (when-let [join-msg (<! in)]
        (if (and (instance? GameJoinMessage join-msg) (msg/valid? join-msg))
          (let [player-id (:player-id join-msg)
                assoc-player-id #(if (associative? %) (assoc % :player-id player-id) %)
                player-in (map-source assoc-player-id in)
                client {:in player-in :out out :player-id player-id}]
            (join-game! game-envs game-id join-msg client))
          (do
            (>! out (msg/->ErrorMessage "You're strange"))
            (close! out)))))
    (close! out)))

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
