(ns ^:shared ji.domain.game
  (:require [ji.domain.player :as p]
            [ji.util :as util :refer [now]]
            [clojure.set :as s]))

(def shapes  [:oval :squiggle :diamond])
(def colors  [:red :purple :green])
(def numbers [1 2 3])
(def fills   [:solid :striped :outlined])
(def features {:shape shapes
               :color colors
               :number numbers
               :fill fills})

(defrecord Game [deck board players])

(defn new-deck []
  (for [s shapes c colors n numbers f fills]
    {:shape s :color c :number n :fill f}))

(defn is-set? [cs]
  (every? #(let [fs (map % cs)]
             (or (apply = fs) (= (count (set fs)) (count fs))))
          (keys features)))

(defn solve-feature
  "Returns value for feature to match other 2 values"
  [feature v1 v2]
  (if (= v1 v2) v1
    (some #(when (and (not= % v1) (not= % v2)) %) (features feature))))

(defn solve-set
  "Returns card that completes the set"
  [c1 c2]
  (reduce (fn [c3 feat]
            (assoc c3 feat (solve-feature feat (feat c1) (feat c2))))
          c1 (keys features)))

(defn solve-board
  "Returns lazy sequence of sets on board"
  [board]
  (let [solve-pair (fn [[c1 c2]]
                     (if-let [c3 (board (solve-set c1 c2))]
                       #{c1 c2 c3}))]
    (->> (for [a board b board :when (not= a b)] [a b])
         (map solve-pair)
         (keep identity)
         (distinct))))

(defn new-game []
  (map->Game {:deck (-> (new-deck) (shuffle))
              :board #{}
              :players {}}))

(defn game-over? [{:keys [deck board]}]
  (empty? (solve-board (into board deck))))

(defn player [game player-id]
  (get-in game [:players player-id]))

(defn update-player [game player-id f & args]
  (apply update-in game [:players player-id] f args))

(defn add-player [game player-id]
  (if-let [plr (player game player-id)]
    (if (p/offline? plr)
      (update-player game player-id p/go-online)
      game)
    (update-in game [:players] assoc player-id (p/new-player))))

(defn remove-player [game player-id]
  (update-in game [:players] dissoc player-id))

(defn disconnect-player [game player-id]
  (if player-id
    (update-player game player-id p/go-offline)
    game))

(defn has-player? [game player-id]
  (contains? (:players game) player-id))

(defn player-offline? [game player-id]
  (if-let [plr (player game player-id)]
    (p/offline? plr)
    true))

(defn take-set [game player-id cards]
  (println "TAKE SET" player-id cards)
  (-> game
      (update-in [:board] s/difference (set cards))
      (update-player player-id update-in [:sets] conj cards)))

(defn revoke-set [game player-id]
  (println "REVOKE SET" player-id)
  (update-player game player-id update-in [:sets] drop-last))

(defn draw-cards [n {:keys [deck board] :as game}]
  (assoc game
         :deck (drop n deck)
         :board (into board (take n deck))))

(defn valid-set? [game cards]
  (and (s/subset? cards (:board game))
       (is-set? cards)))
