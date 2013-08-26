(ns ji.core
  (:require [ji.domain :refer [new-deck solve-board is-set?]]
            [dommy.core :as dom]
            [cljs.core.async :as async
             :refer [<! >! chan close! put! timeout]]
            [ji.util.helpers :refer [event-chan map-chan copy-chan demux into-chan]])
  (:require-macros
    [dommy.macros :refer [sel sel1 deftemplate node]]
    [cljs.core.async.macros :as m :refer [go alt!]]
    [ji.util.macros :refer [go-loop]]))
;; vim: setlocal lispwords+=go,go-loop

(defn separate [n coll] [(take n coll) (drop n coll)])
(defn hash-by [key-fn val-fn coll] (into {} (for [item coll] [(key-fn item) (val-fn item)])))

(let [m {:solid "f" :striped "s" :outlined "e"
         :oval "o" :squiggle "s" :diamond "d"
         :red "r" :green "g" :purple "b"}]
 (deftemplate card-tmpl [{:keys [shape color number fill] :as card}]
  [:div.card
   [:img
    {:src (str "cards/" number (m fill) (m color) (m shape) ".png")}]]))


(defn card-selector
  "todo: general partition/chunking buffer"
  [card-chan control-chan]
  (let [c (chan)]
    (go
      (loop [cs #{}]
        (let [[v sc] (alts! [card-chan control-chan])]
          (if (= sc control-chan)
            (recur #{}) ;; currently only reset
            (let [cs' (conj cs v)]
              (cond
                (= cs cs') (recur (disj cs v))
                (<= 3 (count cs')) (do
                                    (>! c cs')
                                    (recur #{}))
                :else (recur cs')))))))
    c))


(defn add-card!
  [card parent-el out]
  (let [el (card-tmpl card)
        eh #(put! out card)]
    (dom/append! parent-el el)
    (dom/listen! el :click eh)
    {:card card
     :el el
     :unsubscribe #(dom/unlisten! el :click eh)}))

(defn add-cards!
  [cards parent-el out]
  (map #(add-card! % parent-el out) cards))

(defn render-solutions!
  [sets]
  (let [el (node [:div#solution [:h4 "Sets"]])]
    (if-let [x (sel1 :#solution)] (dom/remove! x))
    (dom/append! (sel1 :#content) el)
    (doseq [s sets]
      (dom/append! el (node [:div.set (map card-tmpl s)])))))

(defn init []
  (let [[board deck] (->> (new-deck) (shuffle) (separate 12))
        control (chan)
        card-sel (chan)
        set-sel (card-selector card-sel control)
        valid-sets (chan)
        board (doall (map #(add-card! % (sel1 :#board) card-sel) board))

        clear-selection #(doseq [el (sel :.card.selected)]
                           (dom/remove-class! el "selected"))
        ]

    (go-loop
      (let [cs (<! set-sel)]
        (clear-selection)
        (when (is-set? cs)
          (>! valid-sets cs))))

    (go
      (loop [deck deck
             board board]
        (let [solutions (solve-board (map :card board))]
         (render-solutions! solutions))

        (let [card-unsubs (hash-by :card :unsubscribe board) ;; todo
              card-els (hash-by :card :el board) ;; todo
              cs (<! valid-sets)]
          (doseq [card cs :let [el (card-els card)] ]
            ((card-unsubs card))
            (dom/remove! el))
          (dom/append! (sel1 :#set-history)
                       (node [:li.set (map card-tmpl cs)]))
          (recur (drop 3 deck)
                 (concat (remove #(cs (:card %)) board)
                         (add-cards! (take 3 deck) (sel1 :#board) card-sel))))
        ))

    (dom/listen! [(sel1 :#content) :.card]
                 :click
                 #(dom/toggle-class! (dom/closest (.-target %) :.card) "selected"))

    ))

(init)
