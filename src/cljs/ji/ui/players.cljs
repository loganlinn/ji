(ns ji.ui.players
  (:require [ji.ui :as ui]
            [ji.domain.player :as p]
            [ji.domain.game :as game :refer [player-offline?]]
            [ji.domain.messages :as msg]
            [ji.ui.card :as card]
            [ji.util.helpers :refer [event-chan]]
            [clojure.set :as s]
            [clojure.string :as str]
            [cljs.core.async :as async
             :refer [<! >! chan close! put! timeout]]
            [cljs.core.match]
            [dommy.core :as dom])
  (:require-macros
    [dommy.macros :refer [sel sel1 deftemplate node]]
    [cljs.core.async.macros :refer [go alt!]]
    [cljs.core.match.macros :refer [match]]))

(defn player-order
  "Orders the players"
  [player-id players]
  (sort-by (fn [[pid plr]] [(= player-id pid)
                            (p/online? plr)
                            (count (:sets plr))
                            pid])
           >
           players))

(deftemplate player-tmpl [player-id player sets is-self?]
  (let [online? (p/online? player)]
    [:li.player
     {:data-player-id player-id
      :class (str/join " " [(if online? "online" "offline")
                            (if is-self? "self")])}
     [:h5
      [:span.hide-for-small player-id]
      [:span.player-id-abbr.show-for-small (-> player-id first str/upper-case)]]
     [:span.online-ind]
     [:span.subheader (str "score: " (:score player))]
     [:div.sets
      (map card/set-tmpl (take 9 sets))]]))

(deftemplate players-tmpl [player-id players sets]
  (let [sets-by-pid (group-by :player-id sets)
        player-sets #(map :cards (sets-by-pid %))]
    [:div.row.collapse
     [:ul.players
      (map (fn [[pid player]]
             (player-tmpl pid player (player-sets pid) (= player-id pid)))
           (player-order player-id players))]]))

(defn go-players-ui [container player-id players-chan]
  (go (loop [players {}]
        (match (<! players-chan)
          nil players

          {:players players' :sets sets}
          (if (= players players')
            (recur players)
            (let [node (players-tmpl player-id players' sets)]
              (dom/replace-contents! container node)
              (recur players')))

          :else (recur players)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;; Records

(defrecord PlayersComponent [player-id players-chan]
  ui/IComponent
  (attach! [component container]
    (->> (players-tmpl player-id {} [])
         (dom/replace-contents! container))
    (go-players-ui container player-id players-chan))
  (destroy! [component container exit-data]
    (dom/set-html! container "")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;; Public

(defn create [player-id players-chan]
  (->PlayersComponent player-id players-chan))
