(ns ji.service.templates
  (:require [clojure.string :as str]
            [hiccup.core :refer [html]]
            [hiccup.page :as page]
            [hiccup.element :as element]
            [hiccup.util :refer [url]]))

(defn game-url
  ([] (url "/games"))
  ([id] (url "/games/" id)))

(defn render-game [game-env]
  (html [:body#game.row.collapsed
         {:onload "ji.main.init();"
          :data-game-id (:id game-env)}
         [:div#messages.small-12.columns]
         [:div.small-12.columns
          [:div#content.row.collapsed]]
         (page/include-js "/js/main.js")]))

(defn render-game-create [game-id]
  (html [:form
         {:method "POST"
          :action (game-url)}
         [:input {:type "hidden" :name "game-id" :value game-id}]
         [:div.row
          [:div.large-6.small-12.columns
           [:input.button
            {:type "submit"
             :value "Create Game"}]]]]))

(defn lobby-game [{:keys [id game clients] :as game-env}]
  (html [:li.game
         [:span.players [:span.label "Players:"] (count clients)]
         id
         [:a.button {:href (game-url id)} "Join"]]))

(defn render-lobby [game-envs]
  (html
    [:div#lobby.row.collapsed
     (render-game-create "lobby-game") ;; todo
     (->> (for [game-env (vals game-envs)] @game-env)
          (map lobby-game)
          (element/unordered-list))]))

