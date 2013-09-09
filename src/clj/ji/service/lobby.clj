(ns ji.service.lobby
  (:require [clojure.string :as str]
            [hiccup.core :refer [html]]))

(defn game-env-tmpl [{:keys [game-id] :as game-env}]
  (html [:li.game
         game-id]))

(defn lobby-tmpl [games]
  (html [:ul games]))

(defn render-lobby [game-envs]
  (->> (for [[game-id game-env] game-envs]
         (assoc game-env :game-id game-id))
       (map game-env-tmpl)
       (str/join "")
       (lobby-tmpl)))
