(ns ji.service
  (:require [ji.domain.game :as game]
            [ji.domain.messages :as msg]
            [ji.util.async :refer [map-source map-sink]]
            [com.keminglabs.jetty7-websockets-async.core :as ws]
            [clojure.edn :as edn]
            [clojure.core.async :refer [chan go <! >! <!! >!!]]
            [clojure.core.match :refer [match]]
            [compojure.core :refer [routes]]
            [compojure.route :as route])
  (:import [ji.domain.messages JoinGameMessage]))

(def app
  (routes
    (route/files "/" {:root "public"})))

(def data-readers
  {'ji.domain.messages.ErrorMessage #'msg/map->ErrorMessage
   'ji.domain.messages.JoinGameMessage #'msg/map->JoinGameMessage
   'ji.domain.message.GameStateMessage #'msg/map->GameStateMessage
   'ji.domain.messages.RequestControlMessage #'msg/map->RequestControlMessage
   'ji.domain.messages.SubmitSetMessage #'msg/map->SubmitSetMessage})

(defn client-read-string [data]
  (edn/read-string {:readers data-readers} data))


(defn add-client! [clients game-id player-id client]
  (println "Player" player-id "connected")
  (swap! clients update-in [game-id] assoc player-id client)
  (>!! (:out client) (msg/map->GameStateMessage {})))

(defn remove-client! [clients game-id player-id]
  (swap! clients update-in [game-id] dissoc player-id))

(defn game-uri [uri] uri) ;; todo actually extract gameid


(defn client-join
  [clients {:keys [uri in out] :as client}]
  (go
    (when-let [join-msg (<! in)]
      (if (instance? JoinGameMessage join-msg)
        (do
          (add-client! clients (game-uri (:uri client)) (:player-id join-msg) client)
          join-msg)
        (>! out (msg/->ErrorMessage "You're strange"))))))

(defn wrap-client-chan
  "Reverse the in/out for our sanity, and communicate via edn"
  [{:keys [in out] :as client}]
  (assoc client
         :in (map-source client-read-string out)
         :out (map-sink pr-str in)))

(defn register-ws-app!
  [clients client-chan]
  (go (while true
        (->> (<! client-chan)
             (wrap-client-chan)
             (client-join clients)))))

(comment
  (match [conn]
         [{:uri uri :in in :out out}]
         (go
           (>! in (str "Yo, " uri))
           (loop []
             (when-let [msg (<! out)]
               (prn msg)
               (recur))))))
