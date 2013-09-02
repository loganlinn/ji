(ns ji.ui.players
  (:require [ji.domain.game :as game]
            [ji.domain.messages :as msg]
            [ji.ui.card :refer [card-tmpl]]
            [ji.util.helpers
             :refer [event-chan map-source map-sink copy-chan into-chan]]
            [clojure.set :as s]
            [cljs.core.async :as async
             :refer [<! >! chan close! put! timeout]]
            [cljs.core.match]
            [dommy.core :as dom])
  (:require-macros
    [dommy.macros :refer [sel sel1 deftemplate node]]
    [cljs.core.async.macros :refer [go alt!]]
    [cljs.core.match.macros :refer [match]]
    [ji.util.macros :refer [go-loop]]))

(deftemplate player-tmpl [player-id sets]
  [:li.player
   {:data-player-id player-id}
   [:h4 player-id]
   [:span.subheader (format "%d sets" (count sets))]
   [:ul.sets
    (for [ji (take 9 sets)]
      [:li (map card-tmpl ji)])]])

(deftemplate players-tmpl [player-id players]
  [:div.players
   [:h2 "players"]
   [:ul
    (map #(player-tmpl (key %) (:sets (val %))) players)]])

(defn go-players-ui [container players-chan render-fn]
  (go (loop [players {}]
        (if-let [players' (<! players-chan)]
          (let [node (render-fn players')]
            (dom/remove! (sel1 container :.players))
            (dom/append! (sel1 :#content) node)
            (recur players'))
          players))))

(defn create! [container player-id players-chan]
  (let [renderer #(players-tmpl player-id %)]
    (dom/append! container (renderer []))
    (go-players-ui container players-chan renderer)))

(defn destroy!
  [c container]
  (go (<! c)
      (dom/remove! (sel1 container :.players))))
