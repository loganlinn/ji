(ns ji.main
  (:require [ji.domain.game :as game
             :refer [new-deck solve-board is-set?]]
            [ji.domain.messages :as msg]
            [ji.websocket :as websocket]
            [ji.util.helpers
             :refer [event-chan map-source map-sink copy-chan into-chan]]
            [clojure.set :as s]
            [cljs.core.async :as async
             :refer [<! >! chan close! put! timeout]]
            [cljs.core.match]
            [cljs.reader :refer [register-tag-parser!]]
            [dommy.core :as dom])
  (:require-macros
    [dommy.macros :refer [sel sel1 deftemplate node]]
    [cljs.core.async.macros :refer [go alt!]]
    [cljs.core.match.macros :refer [match]]
    [ji.util.macros :refer [go-loop]]))

(register-tag-parser! 'ji.domain.game.Game game/map->Game)
(register-tag-parser! 'ji.domain.messages.ErrorMessage msg/map->ErrorMessage)
(register-tag-parser! 'ji.domain.messages.GameLeaveMessage msg/map->GameLeaveMessage)
(register-tag-parser! 'ji.domain.messages.GameStateMessage msg/map->GameStateMessage)
(register-tag-parser! 'ji.domain.messages.GameControlMessage msg/map->GameControlMessage)

(defn separate [n coll] [(take n coll) (drop n coll)])

(def current-server (atom nil))

(let [m {:solid "f" :striped "s" :outlined "e"
         :oval "o" :squiggle "s" :diamond "d"
         :red "r" :green "g" :purple "b"}]
 (deftemplate card-tmpl [{:keys [shape color number fill] :as card}]
  [:a.card
   {:href "#"}
   [:img
    {:src (str "cards/" number (m fill) (m color) (m shape) ".png")
     :alt ""}]]))

(deftemplate board-tmpl []
  [:div.board])

(deftemplate join-tmpl []
  [:form.join-game
   [:input {:type "text" :name "player-id" :placeholder "player name" :maxlength 16}]
   [:input {:type "hidden" :name "game-id" :value 1}]
   [:input {:type "submit" :value "join"}]])

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
  [board-el out card]
  (let [el (card-tmpl card)
        eh #(put! out card)]
    (dom/append! board-el el)
    (dom/listen! el :click eh)
    {:card card
     :el el
     :unsubscribe #(dom/unlisten! el :click eh)}))

(defn add-cards!
  [board parent-el out cards]
  (doall (concat board (for [card cards]
                         (add-card! parent-el out card)))))

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

(letfn [(on-card-click [e]
          (-> (.-target e)
              (dom/closest :.card)
              (dom/toggle-class! "selected")))]
  (defn listen-cards! [board-el]
    (dom/listen! [board-el :.card] :click on-card-click))
  (defn unlisten-cards! [board-el]
    (dom/unlisten! [board-el :.card] :click on-card-click)))

(defn go-board-ui
  [container {:keys [+cards -cards card-sel] :as chans}]
  (go
    (loop [board []]
      (let [solutions (solve-board (map :card board))] ;; removeme cheater
        (render-solutions! solutions))
      (match (alts! [+cards -cards])
             [nil _] board
             [v +cards] (recur (add-cards! board container card-sel v))
             [v -cards] (recur (remove-cards! board v))))))

(defn cleanup-board-ui!
  "Receives a board over channel, and cleans up the cards"
  [board-ui board-el]
  (go (doseq [{:keys [unsubscribe el]} (<! board-ui)]
        (unsubscribe)
        (dom/remove! el))
      (unlisten-cards! board-el)
      (dom/remove board-el)))

(defn start-game-solo [{:keys [container]}]
  (let [control (chan)
        card-sel (chan)
        valid-sets (valid-sets-chan (card-selector card-sel control))
        [board deck] (->> (new-deck) (shuffle) (separate 12))
        new-cards (chan)
        old-cards (chan)
        board-el (board-tmpl)]

    (put! new-cards board)
    (-> (go-board-ui board-el {:+cards new-cards
                               :-cards old-cards
                               :card-sel card-sel})
        (cleanup-board-ui! board-el))
    (go
      (loop [deck deck]
        (let [cs (<! valid-sets)]
          (put! old-cards cs)
          (put! new-cards (set (take (count cs) deck)))
          (recur (drop (count cs) deck)))))

    (dom/append! container board-el)

    (listen-cards! board-el)))

(defn go-game [container in out]
  (let [control (chan)
        card-sel (chan)
        sels (card-selector card-sel control)
        +cards (chan)
        -cards (chan)
        board-el (board-tmpl)]

    (go-loop
      (when-let [cards (<! sels)]
        (println "Selected" cards)
        (>! out (msg/->PlayerSetMessage cards))
        (doseq [el (sel :.card.selected)]
          (dom/remove-class! el "selected"))))

    (-> (go-board-ui board-el {:+cards +cards
                               :-cards -cards
                               :card-sel card-sel})
        (cleanup-board-ui! board-el))

    (go (loop [board #{}]
          (when-let [msg (<! in)]
            (println "INPUT" msg)
            (cond
              (instance? msg/GameStateMessage msg)
              (let [board* (set (get-in msg [:game :board]))
                    new-cards (s/difference board* board)
                    old-cards (s/difference board board*)]
                (>! +cards new-cards) (println "new" (count new-cards) new-cards)
                (>! -cards old-cards) (println "old" (count old-cards) old-cards)
                (recur board*))

              :else
              (do (println "UNHANDLED MSG" msg)
                  (recur board)))
            )))

    (dom/append! container board-el)
    (listen-cards! board-el)))

(defn join-game
  [game-id player-id]
  (println "Joining as" player-id)
  ;; todo: clear existing server, if any
  (let [ws-uri (str "ws://" (aget js/window "location" "host") "/game/2")]
    (go
      (let [{:keys [in out] :as server} (<! (websocket/connect! ws-uri))]
        (reset! current-server server)
        (>! out (msg/map->GameJoinMessage {:player-id player-id}))
        ;; TODO get join confirmation
        (go-game (sel1 :#content) in out)))))


(defn ^:export init []
  (dom/append! (sel1 :#content) (join-tmpl))
  (dom/listen! (sel1 :#content) :submit
               (fn [e] (.preventDefault e)
                 (->> [(sel1 (.-target e) "input[name='game-id']")
                       (sel1 (.-target e) "input[name='player-id']")]
                      (map dom/value)
                      (apply join-game)))))
