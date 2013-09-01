(ns ^:shared ji.domain.game
  (:require [clojure.set :as s]))

(def shapes  [:oval :squiggle :diamond])
(def colors  [:red :purple :green])
(def numbers [1 2 3])
(def fills   [:solid :striped :outlined])
(def features {:shape shapes
               :color colors
               :number numbers
               :fill fills})

(defn now [] :todo)

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
  "Returns sets in board"
  [board]
  (let [s-board (set board)]
    (reduce (fn [sets [a b]]
              (let [c (solve-set a b)]
                (if (contains? s-board c)
                  (conj sets #{a b c})
                  sets)))
            #{}
            (for [a board b board :when (not= a b)] [a b]))))

(defrecord Game [deck board players])

(defn new-game []
  (map->Game {:deck (-> (new-deck) (shuffle))
              :board #{}
              :players {}}))

(defn game-over? [game] (empty? (:deck game)))

(defn player [game player-id]
  (get-in game [:players player-id]))

(defn add-player [game player-id]
  (update-in game [:players] assoc player-id {:sets []}))

(defn remove-player [game player-id]
  (update-in game [:players] dissoc player-id))

(defn update-player [game player-id f & args]
  (apply update-in game [:players player-id] f args))

(defn disconnect-player [game player-id]
  (if player-id
    (let [p (player game player-id)
          p (assoc p :since (now))]
     (-> game
        (remove-player player-id)
        (assoc-in [:players-offline player-id] (player game player-id))))
    game))

(defn disconnected-player? [game player-id]
  (contains? (:players-offline game) player-id))

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
