(ns ji.main
  (:require [ji.domain.game :refer [new-deck solve-board is-set?]]
            [ji.domain.messages :as msg]
            [ji.websocket :as websocket]
            [dommy.core :as dom]
            goog.net.WebSocket
            [cljs.core.async :as async
             :refer [<! >! chan close! put! timeout]]
            [cljs.core.match]
            [cljs.reader :refer [register-tag-parser!]]
            [ji.util.helpers :refer [event-chan map-source map-sink copy-chan into-chan]])
  (:require-macros
    [dommy.macros :refer [sel sel1 deftemplate node]]
    [cljs.core.async.macros :refer [go alt!]]
    [cljs.core.match.macros :refer [match]]
    [ji.util.macros :refer [go-loop]]))

(register-tag-parser! 'ji.domain.messages.ErrorMessage msg/map->ErrorMessage)
(register-tag-parser! 'ji.domain.messages.GameLeaveMessage msg/map->GameLeaveMessage)
(register-tag-parser! 'ji.domain.messages.GameStateMessage msg/map->GameStateMessage)
(register-tag-parser! 'ji.domain.messages.GameControlMessage msg/map->GameControlMessage)

(defn separate [n coll] [(take n coll) (drop n coll)])

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

(defn remove-cards!
  [board cards]
  (let [{others false cs true} (group-by #(contains? cards (:card %)) board)]
    (doseq [{:keys [unsubscribe el]} cs]
      (unsubscribe)
      (dom/remove! el))
    others))

(defn board-loop
  [board-el {:keys [cards-in cards-out card-sel] :as chans}]
  (go
    (loop [board []]
      (let [solutions (solve-board (map :card board))] ;; removeme cheater
        (render-solutions! solutions))
      (match (alts! [cards-in cards-out])
        [nil _] board
        [v cards-in] (recur (concat board (add-cards! board-el card-sel v)))
        [v cards-out] (recur (remove-cards! board v))))))

(defn cleanup-board!
  "Receives a board over channel, and cleans up the cards"
  [board-chan]
  (go (let [board (<! board-chan)]
        (println "Cleaning up board")
        (doseq [{:keys [unsubscribe el]} board]
          (unsubscribe)
          (dom/remove! el)))))

(deftemplate board-tmpl []
  [:div.board])

(defn start-game [{:keys [ws-uri container]}]
  (let [control (chan)
        card-sel (chan)
        valid-sets (valid-sets-chan (card-selector card-sel control))
        [board deck] (->> (new-deck) (shuffle) (separate 12))
        new-cards (chan)
        old-cards (chan)
        board-el (board-tmpl)]

    (put! new-cards board)
    (-> (board-loop board-el {:cards-in new-cards
                              :cards-out old-cards
                              :card-sel card-sel})
        (cleanup-board!))
    (go
      (loop [deck deck]
        (let [cs (<! valid-sets)]
          (put! old-cards cs)
          (put! new-cards (set (take (count cs) deck)))
          (recur (drop (count cs) deck)))))

    (dom/append! container board-el)

    (dom/listen! [(sel1 :#content) :.card] :click
                 #(-> (.-target %)
                      (dom/closest :.card)
                      (dom/toggle-class! "selected")))
    ))


(defn join-game
  []
  ;; get player name
  ;; attempt join
  ;; emit game
  )

(defn ^:export init []
  ;(println )
  (let [ws-uri (str "ws://" (aget js/window "location" "host") "/game/2")]
    (go
      (let [{:keys [in out]} (<! (websocket/connect! ws-uri))]
        (println "Joining...")
        (>! out (msg/map->GameJoinMessage {:player-id "logan"}))
        (println "Recieved:" (<! in))
        (println "Doneski"))))

  (start-game {:container (sel1 :#content)})
  )

