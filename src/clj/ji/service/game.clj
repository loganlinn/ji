(ns ji.service.game
  (:require [clojure.string :as str]
            [hiccup.core :refer [html]]
            [hiccup.page :as page]
            [hiccup.util :refer [url]]))

(defn render-game [game-env]
  (html [:body#game.row.collapsed
         {:onload "ji.main.init();"}
         [:div#messages.small-12.columns]
         [:div.small-12.columns
          [:div#content.row.collapsed]]
         (page/include-js "/js/main.js")]))
