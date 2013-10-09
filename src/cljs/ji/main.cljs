(ns ji.main
  (:require [ji.domain.game :as game
             :refer [new-deck solve-board is-set?]]
            [ji.domain.player :as player]
            [ji.domain.messages :as msg]
            [ji.ui :as ui]
            [ji.ui.card :as card-ui]
            [ji.ui.board :as board-ui]
            [ji.ui.players :as players-ui]
            [ji.ui.status :as status-ui]
            [ji.websocket :as websocket]
            [ji.util.helpers
             :refer [clear! event-chan map-source map-sink copy-chan into-chan]]
            [clojure.set :as s]
            [clojure.string :as str]
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

(def heartbeat-interval 5000)
(def heartbeat-req :ping)
(def heartbeat-resp :pong)

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

(deftemplate game-tmpl []
  [:div.row.collapse
   [:div.large-3.small-2.columns
    [:div#game-status]
    [:div#players]]
   [:div.large-9.small-10.columns
    [:div#board]]])

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
             (println "selected" v)
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
            (doseq [el (sel [:#board :.selected])]
              (dom/remove-class! el "selected"))
            (recur))))))

(defn render-solutions!
  [sets]
  (let [el (node [:div.panel [:h2 "Hints"]])]
    (if-let [x (sel1 :#solution)] (dom/remove! x))
    (dom/append! (sel1 :#board)
                 (node [:div#solution.row.collapse el]))
    (doseq [s sets]
      (dom/append! el (card-ui/set-tmpl s)))))

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

(defn go-game-state
  "Distributes new game state to UI components"
  [game board-state player-state status-state]
  (go
    (>! board-state (select-keys game [:board :sets]))
    (>! player-state (select-keys game [:players :sets]))
    (>! status-state (select-keys game [:cards-remaining :sets]))

    (render-solutions! (solve-board (:board game))) ;; removeme cheater

    game))

(defn go-game [container in out player-id]
  (let [card-sel (chan)
        board-container (sel1 container :#board)
        board-state (chan)
        player-container (sel1 container :#players)
        player-state (chan)
        status-container (sel1 container :#game-status)
        status-state (chan)]
    (go-emit-selections out card-sel)

    (ui/run-component! (board-ui/create board-state card-sel)
                       board-container)
    (ui/run-component! (players-ui/create player-id player-state)
                       player-container)
    (ui/run-component! (status-ui/create status-state)
                       status-container)

    (let [el (node [:button#disable-board.button "Disable Board"])]
      (dom/append! (sel1 :body) el)
      (dom/listen! el :click #(put! board-state :disable)))
    (let [el (node [:button#enable-board.button "Enable Board"])]
      (dom/append! (sel1 :body) el)
      (dom/listen! el :click #(put! board-state :enable)))

    ;; Heartbeat
    (go-loop
      (<! (timeout heartbeat-interval))
      (>! out heartbeat-req))

    ;; driver loop
    (go (loop []
          (let [msg (<! in)]
            (cond
              (nil? msg)
              (do
                (close! board-state)
                (close! player-state)
                (close! status-state)
                (msg/error "Disconnected from server"))

              (= msg heartbeat-resp)
              (do (recur))

              (instance? msg/GameStateMessage msg)
              (do (go-game-state (:game msg) board-state player-state status-state)
                  (recur))

              (instance? msg/GameFinishMessage msg)
              (let [{:keys [game]} msg]
                (>! board-state :disable)
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
  (let [container (sel1 :#game)
        join-submit (chan)
        game-id (dom/attr container "data-game-id")]
    (dom/replace-contents! container (join-tmpl game-id))
    (dom/listen-once! (sel1 :form.join-game) :submit
                      (fn [e] (.preventDefault e) (put! join-submit e)))
    (go (let [e (<! join-submit)
              t (.-target e)
              game-id (dom/value (sel1 t "input[name='game-id']"))
              player-id (-> (sel1 t "input[name='player-id']")
                            (dom/value) (str/trim))
              container (dom/replace-contents! container (game-tmpl))
              result-chan (run-game! container game-id player-id)]
          (clear! (sel1 :#messages))
          (let [result (<! result-chan)]
            (if (msg/error? result)
              (show-alert! (:message result)))))))

  (let [dict "abcdefghijklmnopqrstuvwxyz"
        dict "lj"
        username (apply str (for [x (range 3)] (rand-nth dict)))]
    (go (<! (timeout 50))
        (dom/set-value! (sel1 "input[name='player-id']") username)
        (<! (timeout 12))
        (dom/fire! (sel1 "input[type=submit]") :click)))
  )
