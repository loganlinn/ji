(ns ji.domain.player
  (:require [ji.util :as util :refer [now]]))

(defrecord Player [score])

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

(defn take-set [player cards]
  (assoc player :score (inc (:score player 0))))

(defn revoke-set [player]
  (assoc player :score (max 0 (dec (:score player 0)))) )

(defn new-player []
  (-> (->Player 0)
      (go-online)))
