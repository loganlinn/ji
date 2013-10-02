(ns ji.ui.board
  (:require [ji.domain.game :as game
             :refer [new-deck solve-board is-set?]]
            [ji.domain.messages :as msg]
            [ji.ui.card :as card]
            [ji.util.helpers
             :refer [event-chan map-source map-sink copy-chan into-chan]]
            [clojure.set :as s]
            [cljs.core.async :as async
             :refer [<! >! chan close! put! timeout]]
            [cljs.core.match]
            [dommy.core :as dom])
  (:require-macros
    [dommy.macros :refer [sel sel1 deftemplate node]]
    [cljs.core.async.macros :refer [go alt!]]
    [cljs.core.match.macros :refer [match]]
    [ji.util.macros :refer [go-loop]]))

(defn abs-offset-top
  "Computes an element's offset from top from of page"
  [elem]
  (->> (dom/ancestor-nodes elem)
       (map #(or (.-offsetTop %) 0))
       (reduce + (.-offsetTop elem))))

(defn abs-offset-left
  "Computes an element's offset from left from of page"
  [elem]
  (->> (dom/ancestor-nodes elem)
       (map #(or (.-offsetLeft %) 0))
       (reduce + (.-offsetLeft elem))))

(defn abs-offsets
  [el]
  (reduce (fn [[left top] el]
            [(+ left (or (.-offsetLeft el) 0)) (+ top (or (.-offsetTop el) 0))])
          [(.-offsetLeft el) (.-offsetTop el)]
          (dom/ancestor-nodes el)))

(defn card-selector [card] (str ".card[data-card-id='" (card/card-id card) "']"))
(defn player-selector [player-id] (str "[data-player-id='" player-id "']"))

(deftemplate board-tmpl []
  [:div#board.large-9.small-10.columns
   [:div.row.collapse
    [:div.large-12.columns
     [:ul.cards]]
    [:div.large-12.columns
     [:span.cards-remaining]]]])

(defn bind-card!
  [card-sel {:keys [el card] :as data}]
  (let [eh #(do (.preventDefault %) (put! card-sel card))]
    (dom/listen! el :click eh)
    (assoc data :unsubscribe #(dom/unlisten! el :click eh))))

(defn unbind-card!
  [card]
  (when-let [unsub (:unsubscribe card)] (unsub))
  (dissoc card :unsubscribe))

(defn remove-card!
  [card]
  (let [card (unbind-card! card)]
    (when-let [el (:el card)]
      (go
        (<! (timeout 5000));; TODO use transition event
        (dom/remove! el)))
    (dissoc card :el)))

(defn add-card!
  [board-el card-sel card]
  (let [el (node [:li [:a {:href "#"} (card/card-tmpl card)]])]
    (dom/append! (sel1 board-el :.cards) el)
    (go (dom/add-class! el "new")
        (<! (timeout 2000))
        (dom/remove-class! el "new"))
    (bind-card! card-sel {:card card :el el})))

(defn add-cards!
  [board-data parent-el card-sel cards]
  (if (seq cards)
    (doall (concat board-data
                   (for [card cards]
                     (add-card! parent-el card-sel card))))
    board-data))

(defn remove-cards!
  [board-data cards]
  (if (seq cards)
    (let [{others false cs true} (group-by #(contains? cards (:card %)) board-data)]
      (doall (map remove-card! cs))
      others)
    board-data))

(letfn [(on-card-click [e]
          (.preventDefault e)
          (-> (.-target e)
              (dom/closest :a)
              (dom/toggle-class! "selected")))]
  (defn listen-cards! [board-el]
    (dom/listen! [board-el :a] :click on-card-click))
  (defn unlisten-cards! [board-el]
    (dom/unlisten! [board-el :a] :click on-card-click)))

(defn transition-set! [{:keys [player-id cards]}]
  (if-let [player-el (sel1 [:#players (player-selector player-id)])]
    (let [cards-el (sel1 [:#board :.cards])
          {cards-left :left cards-top :top} (dom/bounding-client-rect cards-el)
          {player-left :left player-top :top} (dom/bounding-client-rect player-el)
          ;; find card-els, backout to immediate ul.cards child
          card-els (->> (map #(sel1 cards-el (card-selector %)) cards)
                        (map (fn [card-el] (some #(if (= cards-el (.-parentNode %)) %)
                                                 (dom/ancestor-nodes card-el)))))
          left (- player-left cards-left)
          top (- player-top cards-top (-> card-els first
                                          dom/bounding-client-rect
                                          :height (/ 4)))]
      (doseq [card-el card-els]
        (dom/set-px! card-el :top top :left left)))))

(defn transition-sets! [sets] (doseq [s sets] (transition-set! s)))

(defn go-board-ui
  [board-el board-state card-sel]
  (go
    (loop [board-data []
           num-sets 0]
      (match (<! board-state)
        nil
        board-data

        :disable
        (do (unlisten-cards! board-el)
            (recur (doall (map unbind-card! board-data))
                   num-sets))

        :enable
        (do (listen-cards! board-el)
            (recur (doall (map #(bind-card! card-sel %) board-data))
                   num-sets))

        [board sets]
        (let [old-board (set (map :card board-data))
              -cards (s/difference old-board board)
              +cards (s/difference board old-board)
              new-sets (->> (drop num-sets sets)
                            (filter #(s/subset? (:cards %) old-board)))]
          (println "new sets" new-sets)
          (transition-sets! new-sets)
          (recur (-> board-data
                     (remove-cards! -cards)
                     (add-cards! board-el card-sel +cards))
                 (count sets)))
        :else
        (recur board-data num-sets)))))

(defn create! [container board-state card-sel]
  (let [board-el (board-tmpl)]
    (dom/append! container board-el)
    (listen-cards! board-el)
    (go-board-ui board-el board-state card-sel)))

(defn destroy!
  [c container]
  (go
    (let [board-data (<! c)]
      (doall (map remove-card! board-data))
      (when-let [board-el (sel1 container :#board)]
        (unlisten-cards! board-el)
        (dom/remove! board-el)))))
