(ns ji.service.client
  (:require [ji.domain.messages :as msg]
            [clojure.edn :as edn]
            [clojure.core.async :refer [close! map> map< put!]]))

(def data-readers
  {'ji.domain.messages.ErrorMessage #'msg/map->ErrorMessage
   'ji.domain.messages.GameJoinMessage #'msg/map->GameJoinMessage
   'ji.domain.messages.GameLeaveMessage #'msg/map->GameLeaveMessage
   'ji.domain.message.GameStateMessage #'msg/map->GameStateMessage
   'ji.domain.messages.GameControlMessage #'msg/map->GameControlMessage
   'ji.domain.messages.PlayerSetMessage #'msg/map->PlayerSetMessage})

(defn client-read-string [data]
  (edn/read-string {:readers data-readers} data))

(defn create-client
  "Reverse the in/out for our sanity, and communicate via edn"
  [{:keys [in out] :as client}]
  (when client
    (assoc client
           :in (map< client-read-string out)
           :out (map> pr-str in))))

(defn disconnect-client!
  ([c msg]
   (put! (:out c) msg #(close! (:out c))))
  ([c]
   (close! (:out c))))
