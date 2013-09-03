(ns ji.domain.player
  (:require [ji.util :as util :refer [now]]))

(defrecord Player [sets])

(defn offline? [player] (boolean (:offline-since player)))
(def online? (complement offline?))

(defn go-online [player]
  (-> player
      (dissoc :offline-since)
      (assoc :online-since (now))))

(defn go-offline [player]
  (-> player
      (dissoc :online-since)
      (assoc :offline-since (now))))

(defn new-player []
  (-> (->Player #{}) (go-online)))
