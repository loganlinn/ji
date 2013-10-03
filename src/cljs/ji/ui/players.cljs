(ns ji.ui.players
  (:require [ji.domain.player :as p]
            [ji.domain.game :as game :refer [player-offline?]]
            [ji.domain.messages :as msg]
            [ji.ui.card :refer [card-tmpl]]
            [ji.util.helpers
             :refer [event-chan map-source map-sink copy-chan into-chan]]
            [clojure.set :as s]
            [clojure.string :as str]
            [cljs.core.async :as async
             :refer [<! >! chan close! put! timeout]]
            [cljs.core.match]
            [dommy.core :as dom])
  (:require-macros
    [dommy.macros :refer [sel sel1 deftemplate node]]
    [cljs.core.async.macros :refer [go alt!]]
    [cljs.core.match.macros :refer [match]]
    [ji.util.macros :refer [go-loop]]))

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
     [:ul.sets
      (for [ji (take 9 sets)] ;; TODO handle revoked sets
        [:li (map card-tmpl ji)])]]))

(defn player-order
  "Orders the players"
  [player-id players]
  (sort-by (fn [[pid plr]] [(= player-id pid)
                            (p/online? plr)
                            (count (:sets plr))
                            pid])
           >
           players))

(deftemplate players-tmpl [player-id players sets]
  ;(println (pr-str (players player-id)))
  ;(println (pr-str (dissoc players player-id)))
  (let [sets-by-pid (group-by :player-id sets)
        player-sets #(map :cards (sets-by-pid %))]
    [:div.players.large-3.small-2.columns
     [:div.row.collapse
      [:ul.large-block-grid-1
       (map (fn [[pid player]]
              (player-tmpl pid player (player-sets pid) (= player-id pid)))
            (player-order player-id players))]]]))

(defn go-players-ui [container player-id players-chan]
  (go (loop [players {}]
        (if-let [[players' sets] (<! players-chan)]
          (if (= players players')
            (recur players)
            (let [node (players-tmpl player-id players' sets)]
              (dom/set-html! container "")
              (dom/append! container node)
              (recur players')))
          players))))

(defn create! [container player-id players-chan]
  (let [players-container (node [:div#players
                                 (players-tmpl player-id {} [])])]
    (dom/prepend! container players-container)
    (go-players-ui players-container player-id players-chan)))

(defn destroy!
  [c container]
  (go (<! c)
      (dom/remove! (sel1 container :.players))))
