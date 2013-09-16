(ns ji.main
  (:require [ji.domain.game :as game
             :refer [new-deck solve-board is-set?]]
            [ji.domain.player :as player]
            [ji.domain.messages :as msg]
            [ji.ui.card :refer [card-tmpl]]
            [ji.ui.board :as board-ui]
            [ji.ui.players :as players-ui]
            [ji.websocket :as websocket]
            [ji.util.helpers
             :refer [clear! event-chan map-source map-sink copy-chan into-chan]]
            [clojure.set :as s]
            [cljs.core.async :as async
             :refer [<! >! chan close! put! timeout]]
            [cljs.core.match]
            [cljs.reader :refer [register-tag-parser!]]
            [dommy.core :as dom]
            [dommy.template :refer [html->nodes]])
  (:require-macros
    [dommy.macros :refer [sel sel1 deftemplate node]]
    [cljs.core.async.macros :refer [go alt!]]
    [cljs.core.match.macros :refer [match]]
    [ji.util.macros :refer [go-loop]]))

(register-tag-parser! 'ji.domain.game.Game game/map->Game)
(register-tag-parser! 'ji.domain.player.Player player/map->Player)
(register-tag-parser! 'ji.domain.messages.ErrorMessage msg/map->ErrorMessage)
(register-tag-parser! 'ji.domain.messages.GameLeaveMessage msg/map->GameLeaveMessage)
(register-tag-parser! 'ji.domain.messages.GameStateMessage msg/map->GameStateMessage)
(register-tag-parser! 'ji.domain.messages.GameFinishMessage msg/map->GameFinishMessage)
;(register-tag-parser! 'ji.domain.messages.GameControlMessage msg/map->GameControlMessage)

(defn separate [n coll] [(take n coll) (drop n coll)])

(deftemplate join-tmpl [game-id]
  [:form.join-game
   [:div.row
    [:div.large-6.columns.large-centered
     [:input {:type "hidden" :name "game-id" :value game-id}]
     [:row.row.collapse
      [:div.small-10.columns
       [:input {:type "text" :name "player-id" :placeholder "player name" :maxlength 16}]]
      [:div.small-2.columns
       [:input.button.postfix {:type "submit" :value "join"}]]]]]])

(defn show-alert!
  [msg]
  (dom/append!
    (sel1 :#messages)
    (node [:div.alert-box {:data-alert true} msg])))

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
            (>! out (msg/->PlayerSetMessage cards))
            (doseq [el (sel [:.board :.selected])]
              (dom/remove-class! el "selected"))
            (recur))))))

(defn render-solutions!
  [sets]
  (let [el (node [:div#solution [:h2 "psst"]])]
    (if-let [x (sel1 :#solution)] (dom/remove! x))
    (dom/append! (sel1 :#content) el)
    (doseq [s sets]
      (dom/append! el (node [:div.set (map card-tmpl s)])))))

(defn go-game-summary [container {:keys [game] :as finish-msg}]
  (println finish-msg)
  (go (let [modal (node [:div.reveal-modal.open
                         {:style "display:block; visibility:visible;"}
                         [:h2 "Game Complete"]
                         [:div "game stats..."]
                         [:a.close-reveal-modal (html->nodes "&#215;")]])
            close-chan (chan)]
        (dom/append! container modal)
        (dom/listen! (sel1 modal :a.close-reveal-modal) :click
                     (fn [e] (.preventDefault e) (close! close-chan)))
        (<! close-chan)
        (dom/remove! modal))))

(defn go-game [container in out player-id]
  (let [card-sel (chan)
        board-state (chan)
        player-state (chan)]
    (go-emit-selections out card-sel)
    (-> (board-ui/create! container board-state card-sel)
        (board-ui/destroy! container))
    (-> (players-ui/create! container player-id player-state)
        (players-ui/destroy! container))
    ;; driver loop
    (go (loop []
          (let [msg (<! in)]
            (cond
              (nil? msg)
              (do
                (close! board-state)
                (close! player-state)
                (msg/error "Disconnected from server"))

              (instance? msg/GameStateMessage msg)
              (let [game* (:game msg)
                    board* (set (:board game*))
                    players* (:players game*)]

                (>! board-state board*)
                (>! player-state players*)
                (dom/set-text! (sel1 [:.board :.cards-remaining])
                               (str "Cards remaining: " (get-in msg [:game :cards-remaining] "?")))
                (render-solutions! (solve-board board*)) ;; removeme cheater
                (recur))

              (instance? msg/GameFinishMessage msg)
              (let [{:keys [game]} msg]
                (<! (go-game-summary container msg))
                (close! board-state)
                (close! player-state)
                msg)

              :else
              (do (println "UNHANDLED MSG" msg)
                  (recur)))
            )))))

(defn run-game!
  [container game-id player-id]
  (let [ws-uri (str "ws://" (aget js/window "location" "host") "/games/" game-id)]
    (go
      (let [{:keys [in out] :as server} (<! (websocket/connect! ws-uri))
            msg (msg/join-game :player-id player-id)]
        (if (msg/valid? msg)
          (do
            (>! out msg)
            ;; TODO get join confirmation
            (<! (go-game container (:in server) (:out server) player-id)))
          (msg/error "Unable to join"))))))


(defn ^:export init []
  (let [container (sel1 :#content)
        join-submit (chan)
        game-id (dom/attr (sel1 :body) "data-game-id")]
    (clear! container)
    (dom/append! container (join-tmpl game-id))
    (dom/listen-once! (sel1 :form.join-game) :submit
                      (fn [e] (.preventDefault e)
                        (put! join-submit e)))
    (go (let [e (<! join-submit)
              t (.-target e)
              game-id (dom/value (sel1 t "input[name='game-id']"))
              player-id (dom/value (sel1 t "input[name='player-id']"))
              result-chan (run-game! container game-id player-id)]
          (clear! (sel1 :#messages))
          (dom/remove! (sel1 :form.join-game))
          (let [result (<! result-chan)]
            (if (msg/error? result)
              (show-alert! (:message result)))))))

  ;(let [dict "abcdefghijklmnopqrstuvwxyz"
  ;username (apply str (for [x (range 5)] (rand-nth dict)))]
  ;(go (<! (timeout 50))
  ;(dom/set-value! (sel1 "input[name='player-id']") username)
  ;(<! (timeout 12))
  ;(dom/fire! (sel1 "input[type=submit]") :click)))
  )
