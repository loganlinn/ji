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
  (let [out (chan)]
    (go
      (loop [cs #{}
             paused? false]
        (let [[v sc] (alts! [card-chan control-chan])]
          (if (= sc control-chan)
            (condp = v
              ;nil (close! out)
              :pause (recur cs true)
              :unpause (recur cs false)
              :reset (recur #{} paused?))
            (cond
              ;(nil? v) (close! out)
              paused? (recur cs paused?)
              (cs v) (recur (disj cs v) paused?)
              (<= 2 (count cs)) (do (>! out (conj cs v))
                                    (recur #{} paused?))
              :else (recur (conj cs v) paused?))))))
    out))


(defn add-card!
  [parent-el out card]
  (let [el (card-tmpl card)
        eh #(put! out card)]
    (dom/append! parent-el el)
    (dom/listen! el :click eh)
    {:card card
     :el el
     :unsubscribe #(dom/unlisten! el :click eh)}))

(defn add-cards!
  [parent-el out cards]
  (doall (for [card cards] (add-card! parent-el out card))))

(defn render-solutions!
  [sets]
  (let [el (node [:div#solution [:h4 "Sets"]])]
    (if-let [x (sel1 :#solution)] (dom/remove! x))
    (dom/append! (sel1 :#content) el)
    (doseq [s sets]
      (dom/append! el (node [:div.set (map card-tmpl s)])))))

(defn valid-sets-chan
  [set-sel]
  (let [out (chan)
        clear-selection #(doseq [el (sel :.card.selected)]
                           (dom/remove-class! el "selected"))]
    (go-loop
      (let [cs (<! set-sel)]
        (clear-selection)
        (when (is-set? cs)
          (>! out cs))))
    out))

(defn game-loop
  [{:keys [deck board]} control card-sel valid-sets]
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
                       (add-cards! (sel1 :#board) card-sel (take 3 deck))))))))

(defn init []
  (let [control (chan)
        card-sel (chan)
        valid-sets (valid-sets-chan (card-selector card-sel control))
        [board deck] (->> (new-deck) (shuffle) (separate 12))
        board (add-cards! (sel1 :#board) card-sel board)]
    (dom/listen! [(sel1 :#content) :.card] :click
                 #(-> (.-target %)
                      (dom/closest :.card)
                      (dom/toggle-class! "selected")))

    (game-loop {:deck deck :board board} control card-sel valid-sets)))

(init)
