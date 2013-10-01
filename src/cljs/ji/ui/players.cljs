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

(deftemplate player-tmpl [self-id [player-id {:keys [sets] :as player}]]
  (let [online? (p/online? player)]
    [:li.player
     {:data-player-id player-id
      :class (str/join " " [(if online? "online" "offline")
                            (if (= self-id player-id) "self")])}
     [:h5 player-id]
     [:span.online-ind]
     [:span.subheader (str (count sets) " sets")]
     [:ul.sets
      (for [ji (take 9 sets)]
        [:li (map card-tmpl ji)])]]))

(deftemplate players-tmpl [player-id players]
  ;(println (pr-str (players player-id)))
  ;(println (pr-str (dissoc players player-id)))
  [:div.players.large-3.small-2.columns
   [:div.row.collapse
    [:ul.large-block-grid-1
     (map (partial player-tmpl player-id)
          (cons
            [player-id (players player-id)]
            (sort-by (fn [[pid plr]]
                       [(p/online? plr) (count (:sets plr)) pid])
                     >
                     (dissoc players player-id))))]]])

(defn go-players-ui [container player-id players-chan]
  (go (loop [players {}]
        (if-let [players' (<! players-chan)]
          (if (= players players')
            (recur players)
            (let [node (players-tmpl player-id players')]
              (dom/remove! (sel1 container :.players))
              (dom/prepend! (sel1 :#content) node)
              (recur players')))
          players))))

(defn create! [container player-id players-chan]
  (dom/prepend! container (players-tmpl player-id {}))
  (go-players-ui container player-id players-chan))

(defn destroy!
  [c container]
  (go (<! c)
      (dom/remove! (sel1 container :.players))))
