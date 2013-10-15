(ns ji.main
  (:require [ji.domain.game :as game
             :refer [new-deck solve-board is-set?]]
            [ji.domain.player :as player]
            [ji.domain.messages :as msg]
            [ji.reader]
            [ji.ui :as ui]
            [ji.ui.card :as card-ui]
            [ji.ui.board :as board-ui]
            [ji.ui.players :as players-ui]
            [ji.ui.status :as status-ui]
            [ji.websocket :as websocket]
            [ji.util.helpers :refer [event-chan]]
            [clojure.set :as s]
            [clojure.string :as str]
            [cljs.core.async :as async
             :refer [<! >! chan close! put! timeout]]
            [cljs.core.match]
            [dommy.core :as dom]
            [dommy.template :refer [html->nodes]])
  (:require-macros
    [dommy.macros :refer [sel sel1 deftemplate node]]
    [cljs.core.async.macros :refer [go go-loop alt!]]
    [cljs.core.match.macros :refer [match]]))

(def heartbeat-interval 7500)
(def heartbeat-req :ping)
(def heartbeat-resp :pong)

(defn game-ws-uri
  "Returns WebSocket URI for game"
  [game-id]
  (str "ws://" (aget js/window "location" "host") "/games/" game-id))

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
   [:div#sidebar-left.large-3.small-2.columns
    [:div#game-status]
    [:div#players]]
   [:div#main.large-9.small-10.columns
    [:div#board]]])

(defn show-alert!
  ([msg] (show-alert! msg nil))
  ([msg alert-type]
   (dom/append!
     (sel1 :#messages)
     (node [:div {:class (str "alert-box " (when alert-type (name alert-type)))
                  :data-alert true}
            msg]))))

(defn card-selector
  "todo: general partition/chunking buffer"
  ([card-chan] (card-selector (chan) card-chan))
  ([c card-chan]
   (go-loop [cs #{}]
     (if (>= (count cs) 3)
       (do (>! c cs) (recur #{}))
       (let [v (<! card-chan)]
         (cond
           (nil? v) (close! c)
           (cs v) (recur (disj cs v))
           :else (recur (conj cs v))))))
   c))

(defn go-emit-selections [out card-sel]
  (let [sels (card-selector card-sel)]
    (go-loop []
      (when-let [cards (<! sels)]
        (>! out (msg/->PlayerSetMessage cards))
        (doseq [el (sel [:#board :.selected])]
          (dom/remove-class! el "selected"))
        (recur)))))

(defn render-solutions!
  [sets]
  (let [el (node [:div.panel [:h2 "Hints"]])]
    (if-let [x (sel1 :#solution)] (dom/remove! x))
    (dom/append! (sel1 :#sidebar-left)
                 (node [:div#solution.row.collapse el]))
    (doseq [s sets]
      (dom/append! el (card-ui/set-tmpl s)))))

(defn go-game-summary [container {:keys [game] :as finish-msg}]
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
  (put! board-state (select-keys game [:board :sets]))
  (put! player-state (select-keys game [:players :sets]))
  (put! status-state (select-keys game [:cards-remaining :sets]))
  (render-solutions! (solve-board (:board game))) ;; removeme cheater
  )

(defn go-emit-heartbeat
  "A go loop to regularly write heartbeat message on channel"
  [control out interval]
  (go-loop []
    (let [tick (timeout interval)]
      (match (alts! [control tick])
        [nil control] nil
        [_ tick] (do (>! out heartbeat-req)
                     (recur))))))

(defn go-game [container in out player-id]
  (let [control (chan) ;; TODO integrate further
        card-sel (chan)
        board-state (chan)
        player-state (chan)
        status-state (chan)]

    ;; Emitters
    (go-emit-heartbeat control out heartbeat-interval)
    (go-emit-selections out card-sel)

    ;; Init components
    (ui/run-component! (board-ui/create board-state card-sel)
                       (sel1 container :#board))
    (ui/run-component! (players-ui/create player-id player-state)
                       (sel1 container :#players))
    (ui/run-component! (status-ui/create status-state)
                       (sel1 container :#game-status))

    ;; main game loop
    (go-loop []
      (let [msg (<! in)]
        (cond
          (nil? msg)
          (do
            (close! control)
            (close! card-sel)
            (close! board-state)
            (close! player-state)
            (close! status-state)
            (dom/set-html! container "")
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
              (recur)))))))

(defn run-game!
  [container game-id player-id]
  (go
    (let [ws-connect (websocket/connect! (game-ws-uri game-id))
          server (<! ws-connect)
          msg (msg/join-game :player-id player-id)]
      (if (msg/valid? msg)
        (do
          (>! (:out server) msg)
          ;; TODO get join confirmation
          (<! (go-game container (:in server) (:out server) player-id)))
        (msg/error "Unable to join")))))

(defn auto-join! []
  (let [dict "abcdefghijklmnopqrstuvwxyz"
        dict "lj"
        username (apply str (for [x (range 3)] (rand-nth dict)))]
    (go (<! (timeout 50))
        (dom/set-value! (sel1 "input[name='player-id']") username)
        (<! (timeout 12))
        (dom/fire! (sel1 "input[type=submit]") :click))))

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
          (dom/clear! (sel1 :#messages))
          (let [result (<! result-chan)]
            (if (msg/error? result)
              (show-alert! (:message result) :alert))))))
  (auto-join!)
  )


(ji.reader/register-tag-parsers!)

;; Janky height
(defn set-content-height []
  (dom/set-px! (sel1 :#content)
               :height (- (.-innerHeight js/window) 45)))
(js/$ set-content-height)
(.resize (js/$ js/window) set-content-height)
