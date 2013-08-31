(ns ji.service
  (:require [ji.domain.game :as game :refer [new-game game-over?]]
            [ji.domain.messages :as msg]
            [ji.util.async :refer [map-source map-sink]]
            [com.keminglabs.jetty7-websockets-async.core :as ws]
            [clojure.edn :as edn]
            [clojure.core.async :refer [chan go <! >! <!! >!! alt! alts! close!]]
            [clojure.core.match :refer [match]]
            [compojure.core :refer [routes]]
            [compojure.route :as route])
  (:import [ji.domain.messages GameJoinMessage]))

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

(defn go-game
  [game join-msgs]
  (go
    (loop [game game
           clients []]
      (if-not (game-over? game)
        (let [[msg sc] (alts! (cons join-msgs (map :in clients)))]
          (if (= sc join-msgs)
            (do
              (println "JOIN MSG" (count clients) msg)
              (recur (game/add-player game (:player-id msg))
                     (conj clients (:client msg))))
            (do
              (println "GAME MSG" msg sc)
              (if-not (nil? msg)
                (recur game clients)
                (recur game (remove #(= (:in %) sc) clients))))
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
              player-in (map-source assoc-player-id in)]
          (join-game! game-envs uri
                      (assoc join-msg :client {:in player-in :out out})))
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
