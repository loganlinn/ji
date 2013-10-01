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

(defn render-error [message]
  (render-page [:div.alert-box.alert message]))

(defn render-game [game-env]
  (page/html5
    (page/include-css "/stylesheets/app.css")
    (html [:body#game.row
           {:onload "ji.main.init();"
            :data-game-id (:id game-env)}
           [:div#messages.small-12.columns]
           [:div.small-12.columns
            [:div#content.row.collapse]]
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

(defn lobby-row [{:keys [id game clients max-clients] :as game-env}]
  (let [num-clients (count clients)]
   (html [:tr
         [:td id]
         [:td.players (format "%d/%d" num-clients max-clients)]
         [:td (when (< num-clients max-clients) [:a.button {:href (game-url id)} "Join"])]])))


(defn render-lobby [game-envs]
  (render-page
    (html
      [:div.row.collapse
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

