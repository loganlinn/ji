(ns ji.service.lobby
  (:require [clojure.string :as str]
            [hiccup.core :refer [html]]
            [hiccup.util :refer [url]]))

(defn game-url [id] (url id))

(defn game-env-tmpl [{:keys [id game clients] :as game-env}]
  (html [:li.game
         [:span.players [:span.label "Players:"] (count clients)]
         id
         [:a.button {:href (game-url id)} "Join"]]))


(defn lobby-tmpl [games]
  (html [:ul games]))

(defn render-lobby [game-envs]
  (->> (for [game-env (vals game-envs)] @game-env)
       (map game-env-tmpl)
       (str/join "")
       (lobby-tmpl)))
