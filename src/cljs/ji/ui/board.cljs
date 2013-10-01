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
  [:div.board.large-9.small-10.columns
   [:div.row.collapse
    [:div.large-12.columns
     [:ul.cards.small-block-grid-3]]
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
      (dom/remove! el))
    (dissoc card :el)))

(defn add-card!
  [board-el card-sel card]
  (let [el (node [:li [:a {:href "#"} (card-tmpl card)]])]
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

(defn go-board-ui
  [board-el board-state card-sel]
  (go
    (loop [board-data []
           last-set nil]
      (if-let [[board* sets] (<! board-state)]
        (cond
          (= :disable board*)
          (do (unlisten-cards! board-el)
              (recur (doall (map unbind-card! board-data))
                     (last sets)))

          (= :enable board*)
          (do (listen-cards! board-el)
              (recur (doall (map #(bind-card! card-sel %) board-data))
                     (last sets)))

          (set? board*)
          (let [board (set (map :card board-data))
                -cards (s/difference board board*)
                +cards (s/difference board* board)]
            (recur (-> board-data
                       (remove-cards! -cards)
                       (add-cards! board-el card-sel +cards))
                   (last sets))))
        board-data))))

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
      (when-let [board-el (sel1 container :.board)]
        (unlisten-cards! board-el)
        (dom/remove! board-el)))))
