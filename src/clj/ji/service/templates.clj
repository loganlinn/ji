(ns ji.service.templates
  (:require [clojure.string :as str]
            [hiccup.core :refer [html]]
            [hiccup.page :as page]
            [hiccup.element :as element]
            [hiccup.util :refer [url]]))

(defn game-url
  ([] (url "/games"))
  ([id] (url "/games/" id)))

(defn render-page [content]
  (page/html5
    (page/include-css "/stylesheets/app.css")
    [:body.row
     [:div.large-12.columns
      content]]))

(defn render-game [game-env]
  (page/html5
    (page/include-css "/stylesheets/app.css")
    (html [:body#game.row.collapsed
           {:onload "ji.main.init();"
            :data-game-id (:id game-env)}
           [:div#messages.small-12.columns]
           [:div.small-12.columns
            [:div#content.row.collapsed]]
           (page/include-js "/js/main.js")])))

(defn render-game-create [game-id]
  (render-page
    (html [:form
           {:method "POST"
            :action (game-url)}
           [:input {:type "hidden" :name "game-id" :value game-id}]
           [:div.row
            [:div.large-6.small-12.columns.large-centered
             [:h2 "Game does not exist ..yet!"]
             [:input.button
              {:type "submit"
               :value "Create Game"}]]]])))

(defn lobby-row [{:keys [id game clients] :as game-env}]
  (html [:tr
         [:td id]
         [:td.players (count clients)]
         [:td [:a.button {:href (game-url id)} "Join"]]]))

(defn render-lobby [game-envs]
  (render-page
    (html
      [:div.row.collapsed
       [:h1 "Games"]
       [:div.large-12.columns
        [:form
         {:method "POST"
          :action (game-url)}
         [:div.row
          [:div.large-6.small-12.columns
           [:input.button
            {:type "submit"
             :value "Create Game"}]]]]]
       [:ul.large-12.columns
        [:table
         [:thead [:tr [:th "Name"] [:th "Players"] [:th ""]]]
         [:tbody (for [game-env (vals game-envs)]
                   (lobby-row @game-env))]]]])))

