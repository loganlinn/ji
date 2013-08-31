(ns ji.domain.game)

(def shapes  [:oval :squiggle :diamond])
(def colors  [:red :purple :green])
(def numbers [1 2 3])
(def fills   [:solid :striped :outlined])
(def features {:shape shapes
               :color colors
               :number numbers
               :fill fills})

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

(defrecord Game [board players])

(defn new-game []
  (->Game (-> (new-deck) (shuffle))
          {}))
