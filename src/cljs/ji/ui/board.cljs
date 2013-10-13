(ns ji.ui.board
  "UI component for game board"
  (:require [ji.ui :as ui]
            [ji.domain.game :as game
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

(defn separate [f coll] [(filter f coll) (filter (complement f) coll)])

(defn bounding-client-rect-diff
  "Implementation detail: merge-with fails on Safari"
  [a b]
  {:left (- (:left a) (:left b))
   :right (- (:right a) (:right b))
   :top (- (:top a) (:top b))
   :bottom (- (:bottom a) (:bottom b))
   :width (- (:width a) (:width b))
   :height (- (:height a) (:height b))})

;;

(def transition-duration 500) ;; ms, defined in app.scss

(defn card-selector [card] (str ".card[data-card-id='" (card/card-id card) "']"))
(defn player-selector [player-id] (str "[data-player-id='" player-id "']"))

(deftemplate board-tmpl []
  [:div.board.row.collapse
   [:div.large-12.columns
    [:ul.cards]]])

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
  (let [el (node [:li [:a.card-target {:href "#"} (card/card-tmpl card)]])]
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

(letfn [(click-handler [e]
          (.preventDefault e)
          (-> (.-target e)
              (dom/closest :.card-target)
              (dom/toggle-class! "selected")))]
  (defn listen-cards! [board-el]
    (dom/listen! [board-el :a] :click click-handler))
  (defn unlisten-cards! [board-el]
    (dom/unlisten! [board-el :a] :click click-handler)))

(defn transition-set! ;; TODO pass container
  "Returns updated board-data after removing the cards assocated with the set
  and transitioning them to element of the player who found it.
  If element for player isn't found, returns board-data unmodified"
  [board-data {:keys [player-id cards]}]
  (assert (set? cards))
  (let [last-set-el (sel1 [:#game-status :.last-set :.set])
        cards-el (sel1 [:.board :.cards])
        [cards-data board-data*] (separate (comp cards :card) board-data)
        {:keys [left top]} (bounding-client-rect-diff last-set-el cards-el)]
    (doseq [card-data cards-data]
      (dom/set-px! (:el card-data) :top top :left left :width 40))
    (go
      (<! (timeout (- transition-duration 50)))
      (doseq [card-data cards-data] (remove-card! card-data)))
    board-data*))

(defn transition-sets! [board-data sets] (reduce transition-set! board-data sets))

(defn go-board-ui
  [board-el board-state card-sel]
  (go
    (loop [board-data []
           num-sets 0]
      (dom/toggle-class! board-el "board-5" (> (count board-data) 12))
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

        {:board board :sets sets}
        (let [old-board (set (map :card board-data))
              -cards (s/difference old-board board)
              +cards (s/difference board old-board)
              new-sets (->> (drop num-sets sets)
                            (filter #(s/subset? (:cards %) old-board)))]
          (recur (-> board-data
                     (transition-sets! new-sets)
                     (remove-cards! -cards)
                     (add-cards! board-el card-sel +cards))
                 (count sets)))
        :else
        (recur board-data num-sets)))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;; Records

(defrecord BoardComponent [board-state card-sel]
  ui/IComponent
  (attach! [component container]
    (let [board-el (board-tmpl)]
      (dom/replace-contents! container board-el)
      (listen-cards! board-el)
      (go-board-ui board-el board-state card-sel)))
  (destroy! [component container exit-data]
    (doall (map remove-card! exit-data))
    (unlisten-cards! container)
    (dom/set-html! container "")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;; Public

(defn create [board-state card-sel]
  (->BoardComponent board-state card-sel))
