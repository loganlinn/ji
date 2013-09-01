(ns ji.ui.players
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

(deftemplate player-tmpl [card-tmpl player-id sets]
  (println "player" player-id sets)
  [:li.player
   {:data-player-id player-id}
   [:h4 player-id]
   [:span (format "%d sets" (count sets))]
   [:ul.sets
    (for [ji (take 9 sets)]
      [:li (map card-tmpl ji)])]])

(deftemplate players-tmpl [card-tmpl player-id players]
  (println "players-tmpl" players)
  [:div.players
   [:h2 "players"]
   [:ul
    (map #(player-tmpl card-tmpl (key %) (:sets (val %))) players)]])

(defn go-players-ui [container players-chan render-fn]
  (go (loop [players {}]
        (if-let [players' (<! players-chan)]
          (let [node (render-fn players')]
            (println "players" players players')
            (dom/remove! (sel1 container :.players))
            (dom/append! (sel1 :#content) node)
            (recur players'))
          players))))

(defn create! [container card-tmpl player-id players-chan]
  (let [players {}]
    (dom/append! container (players-tmpl card-tmpl player-id []))
    (go-players-ui container players-chan #(players-tmpl card-tmpl player-id %))))

(defn destroy!
  ([container]
   (dom/remove! (sel1 container :.players)))
  ([c container]
   (go (<! c)
       (destroy! container))))
