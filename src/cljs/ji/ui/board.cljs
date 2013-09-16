(ns ji.ui.board
  (:require [ji.domain.game :as game
             :refer [new-deck solve-board is-set?]]
            [ji.domain.messages :as msg]
            [ji.ui.card :refer [card-tmpl]]
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

(deftemplate board-tmpl []
  [:div.board.row.collapse
   [:div.large-12.columns
    [:ul.small-block-grid-3]]
   [:div.large-12.columns
    [:span.cards-remaining]]])

(defn add-card!
  [board-el card-sel card]
  (let [el (node [:li [:a {:href "#"} (card-tmpl card)]])
        eh #(do (.preventDefault %) (put! card-sel card))]
    (dom/append! (sel1 board-el :ul) el)
    (dom/listen! el :click eh)
    (go (dom/add-class! el "new")
        (<! (timeout 2000))
        (dom/remove-class! el "new"))
    {:card card
     :el el
     :unsubscribe #(dom/unlisten! el :click eh)}))

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
      (doseq [{:keys [unsubscribe el]} cs]
        (unsubscribe)
        (dom/remove! el))
      others)
    board-data))

(letfn [(on-card-click [e]
          (-> (.-target e)
              (dom/closest :a)
              (dom/toggle-class! "selected")))]
  (defn listen-cards! [board-el]
    (dom/listen! [board-el :a] :click on-card-click))
  (defn unlisten-cards! [board-el]
    (dom/unlisten! [board-el :a] :click on-card-click)))

(defn go-board-ui
  [container board-state card-sel]
  (go
    (loop [board-data []]
      (if-let [board* (<! board-state)]
        (let [board (set (map :card board-data))
              -cards (s/difference board board*)
              +cards (s/difference board* board)]
          (recur (-> board-data
                     (remove-cards! -cards)
                     (add-cards! container card-sel +cards))))
        board-data))))

(defn create! [container board-state card-sel]
  (let [board-el (board-tmpl)]
    (dom/append! container board-el)
    (listen-cards! board-el)
    (go-board-ui board-el board-state card-sel)))

(defn destroy!
  [c container]
  (go
    (when-let [board-el (sel1 container :.board)]
      (doseq [{:keys [unsubscribe el]} (<! c)]
        (unsubscribe)
        (dom/remove! el))
      (unlisten-cards! board-el)
      (dom/remove! board-el))))
