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

(defn- card-placeholder [] (node [:div.card-placeholder]))

(deftemplate board-row-tmpl []
  [:div.row.collapsed (for [x (range 3)] [:div.small-4.columns (card-placeholder)])])

(deftemplate board-tmpl []
  [:div.board.small-12.large-8.large-centered.columns
   (for [x (range 4)] (board-row-tmpl))])

(defn add-board-row! [board-el]
  (println "Adding row to board")
  (dom/append! board-el (board-row-tmpl)))

(defn next-placeholder [board-el]
  (if-let [ph (sel1 board-el :.card-placeholder)]
    ph
    (do (add-board-row! board-el)
        (sel1 board-el :.card-placeholder))))

(defn add-card!
  [board-el card-sel card]
  (let [el (card-tmpl card)
        eh #(put! card-sel card)]
    (dom/replace! (next-placeholder board-el) el)
    (dom/listen! el :click eh)
    (go (dom/add-class! el "new")
        (<! (timeout 2000))
        (dom/remove-class! el "new"))
    {:card card
     :el el
     :unsubscribe #(dom/unlisten! el :click eh)}))

(defn add-cards!
  [board parent-el card-sel cards]
  (println "Adding cards:" cards)
  (doall (concat board (for [card cards]
                         (add-card! parent-el card-sel card)))))

(defn remove-cards!
  [board cards]
  (println "removing cards" cards)
  (let [{others false cs true} (group-by #(contains? cards (:card %)) board)]
    (doseq [{:keys [unsubscribe el]} cs]
      (unsubscribe)
      (dom/replace! el (card-placeholder)))
    others))

(letfn [(on-card-click [e]
          (-> (.-target e)
              (dom/closest :.card)
              (dom/toggle-class! "selected")))]
  (defn listen-cards! [board-el]
    (dom/listen! [board-el :.card] :click on-card-click))
  (defn unlisten-cards! [board-el]
    (dom/unlisten! [board-el :.card] :click on-card-click)))

(defn go-board-ui
  [container +cards -cards card-sel]
  (go
    (loop [board []]
      (match (alts! [-cards +cards] :priority true)
             [nil _] board
             [v +cards] (recur (add-cards! board container card-sel v))
             [v -cards] (recur (remove-cards! board v))))))

(defn create! [container +cards -cards card-sel]
  (let [board-el (board-tmpl)]
    (dom/append! container board-el)
    (listen-cards! board-el)
    (go-board-ui board-el +cards -cards card-sel)))

(defn destroy!
  [c container]
  (go
    (doseq [{:keys [unsubscribe el]} (<! c)]
      (unsubscribe)
      (dom/remove! el))
    (let [board-el (sel1 container :.board)]
      (unlisten-cards! board-el)
      (dom/remove board-el))))
