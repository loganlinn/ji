(ns ji.main
  (:require [ji.domain.game :as game
             :refer [new-deck solve-board is-set?]]
            [ji.domain.messages :as msg]
            [ji.ui.card :refer [card-tmpl]]
            [ji.ui.board :as board-ui]
            [ji.ui.players :as players-ui]
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

(deftemplate join-tmpl []
  [:form.join-game
   [:div.row
    [:div.large-6.columns.large-centered
     [:input {:type "hidden" :name "game-id" :value 1}]
     [:row.row.collapse
      [:div.small-10.columns
       [:input {:type "text" :name "player-id" :placeholder "player name" :maxlength 16}]]
      [:div.small-2.columns
       [:input.button.postfix {:type "submit" :value "join"}]]]]]])

(defn card-selector
  "todo: general partition/chunking buffer"
  ([card-chan] (card-selector (chan) card-chan))
  ([c card-chan]
   (go (loop [cs #{}]
         (if (>= (count cs) 3)
           (do (>! c cs) (recur #{}))
           (let [v (<! card-chan)]
             (cond
               (nil? v) (close! c)
               (cs v) (recur (disj cs v))
               :else (recur (conj cs v)))))))
   c))

(defn go-emit-selections [out card-sel]
  (let [sels (card-selector card-sel)]
    (go (loop []
          (when-let [cards (<! sels)]
            (println "Selected" cards)
            (>! out (msg/->PlayerSetMessage cards))
            (doseq [el (sel :.card.selected)]
              (dom/remove-class! el "selected"))
            (recur))))))

(defn render-solutions!
  [sets]
  (let [el (node [:div#solution [:h2 "psst"]])]
    (if-let [x (sel1 :#solution)] (dom/remove! x))
    (dom/append! (sel1 :#content) el)
    (doseq [s sets]
      (dom/append! el (node [:div.set (map card-tmpl s)])))))

(defn go-game [container in out player-id]
  (let [control (chan)
        card-sel (chan)
        +cards (chan)
        -cards (chan)
        *players (chan)]
    (go-emit-selections out card-sel)
    (-> (board-ui/create! container +cards -cards card-sel)
        (board-ui/destroy! container))
    (-> (players-ui/create! container player-id *players)
        (players-ui/destroy! container))
    ;; driver loop
    (go (loop [board #{}]
          (when-let [msg (<! in)]
            (println "INPUT" msg)
            (cond
              (instance? msg/GameStateMessage msg)
              (let [board* (set (get-in msg [:game :board]))
                    players* (get-in msg [:game :players])]
                (>! +cards (s/difference board* board))
                (>! -cards (s/difference board board*))
                (>! *players players*)
                (render-solutions! (solve-board board*)) ;; removeme cheater
                (recur board*))

              :else
              (do (println "UNHANDLED MSG" msg)
                  (recur board)))
            )))))

(defn start-game [container server player-id]
  (go-game container (:in server) (:out server) player-id))

(defn join-game
  [game-id player-id]
  (println "Joining as" player-id)
  ;; todo: clear existing server, if any
  (let [ws-uri (str "ws://" (aget js/window "location" "host") "/game/2")]
    (go
      (let [{:keys [in out] :as server} (<! (websocket/connect! ws-uri))
            msg (msg/join-game :player-id player-id)]
        (if (msg/valid? msg)
          (do (reset! current-server server)
              (>! out msg)
              ;; TODO get join confirmation
              (start-game (sel1 :#content) server player-id))
          (msg/error "Unable to join"))))))


(defn ^:export init []
  (dom/append! (sel1 :#content) (join-tmpl))
  (dom/listen! (sel1 :#content) :submit
               (fn [e] (.preventDefault e)
                 (->> [(sel1 (.-target e) "input[name='game-id']")
                       (sel1 (.-target e) "input[name='player-id']")]
                      (map dom/value)
                      (apply join-game)
                      ;; todo read result of join-game
                      ))))
