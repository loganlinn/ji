(ns ji.service
  (:require [ji.service
             [templates :as tmpl]
             [game-env :as game-env]
             [client :as client]
             [game :as game]]
            [ji.domain.game :refer [new-game game-over?]]
            [ji.domain.messages :as msg]
            [taoensso.timbre :refer [debugf info]]
            [com.keminglabs.jetty7-websockets-async.core :as ws]
            [clojure.core.async :refer [chan go go-loop <! >! <!! >!! alt! alts! put! close! map> map<]]
            [clojure.core.match :refer [match]]
            [hiccup.core :refer [html]]
            [hiccup.page :as page]
            [ring.middleware.params :refer [wrap-params]]
            [ring.util.response :as resp]
            [compojure.core :refer [routes GET POST]]
            [compojure.route :as route]
            [environ.core :refer [env]])
  (:import [ji.domain.messages GameJoinMessage PlayerSetMessage]))

(def max-clients (env :max-clients 128))

(defn num-clients [game-envs]
  (apply + (map #(-> % val deref :clients count) @game-envs)))

(defn accepting-clients? [game-envs]
  (< (num-clients game-envs) max-clients))

(defn generate-game-id [game-envs]
  (let [cs (vec "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ")
        s (apply str (for [i (range 5)] (rand-nth cs)))]
    (if (contains? @game-envs s)
      (recur game-envs)
      s)))

(defn init-game-env!
  [game-envs game-id]
  (let [game-id (or game-id (generate-game-id game-envs))
        game (new-game)
        join-chan (chan)
        game-env (atom (game-env/create-game-env game-id game join-chan))
        game-chan (game/go-game game-env)]
    (swap! game-envs assoc game-id game-env)
    (go (let [finshed-game (<! game-chan)]
          (println "Game finished" finshed-game)
          (swap! game-envs dissoc (:id finshed-game))))
    game-env))

(defn valid-game-id? [game-id]
  (boolean (re-find #"^[\w-]+$" game-id)))

(defn client-join!
  "Reads from new client channel, waits for JoinMessage and connects player to game.
  Returns a channel that will pass valid client or close"
  [game-env {:keys [in out] :as client}]
  (go
    (when-let [join-msg (<! in)]
      (if (and (instance? GameJoinMessage join-msg) (msg/valid? join-msg))
        (if (game-env/accepting-clients? @game-env)
          (let [player-id (:player-id join-msg)
                assoc-player-id #(if (associative? %) (assoc % :player-id player-id) %)
                player-in (map< assoc-player-id in)
                client (assoc client :in player-in :player-id player-id)
                join-msg (assoc join-msg :client client)]
            (>! (:join-chan @game-env) join-msg))
          (do
            (>! out (msg/->ErrorMessage "Game full"))
            (close! out)))
        (do
          (>! out (msg/->ErrorMessage "You're strange"))
          (close! out))))))

(defn register-ws-app!
  [game-envs client-chan]
  (go-loop []
    (when-let [client (client/create-client (<! client-chan))]
      (if-not (accepting-clients? game-envs)
        (client/disconnect-client! client (msg/error "Service temporarily unavailable"))
        (if-let [game-id (second (re-find #"^/games/([\w-]+)" (-> client :request :uri)))]
          (if-let [game-env (@game-envs game-id)]
            (client-join! game-env client)
            (client/disconnect-client! client (msg/error "Unknown game")))
          (client/disconnect-client! client (msg/error "Invalid game id"))))
      (recur))))

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
                  (let [game-env (init-game-env! game-envs game-id)]
                    (resp/redirect-after-post (str "/games/" (:id @game-env)))))))
        (GET "/games" []
             (tmpl/lobby @game-envs))
        (GET "/games/:game-id" [game-id]
             (if-let [game-env (some-> (get @game-envs game-id) deref)]
               (if (game-env/accepting-clients? game-env)
                 (tmpl/game game-env)
                 (tmpl/error "Game full"))
               (route/not-found (tmpl/game-create game-id))))
        (GET "/" [] {:status 302 :headers {"Location" "/games"} :body ""})
        (route/resources "/"))
      (tmpl/wrap-with-request)
      (wrap-params)))
