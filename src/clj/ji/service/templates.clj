(ns ji.service.templates
  (:require [clojure.string :as str]
            [hiccup.core :refer [html]]
            [hiccup.page :as page]
            [hiccup.element :as element]
            [hiccup.util :refer [url]]
            [environ.core :refer [env]]))

(def ^:dynamic *request* nil)

(defmacro with-request
  "Macro to bind a request to rendering scope"
  [request & body]
  `(binding [*request* ~request] ~@body))

(defn wrap-with-request
  "Middleware to bind current request to rendering scope"
  [handler]
  (fn [request]
    (with-request request
      (handler request))))

(defn- build-blocks
  [body]
  (into {} (for [[op & form-body] body]
             (condp = op
               'defblock (let [[block-name & block-body] form-body]
                           [(keyword block-name) block-body])
               :else (throw (RuntimeException. "Unexpected form"))))))

(defmacro defpage
  [title bindings & body]
  (let [blocks (build-blocks body)
        page-title (:title blocks "jí")]
    `(defn ~title ~bindings
       (page/html5
         [:head
          [:title ~page-title]
          (page/include-css "/stylesheets/app.css")
          (page/include-js "/js/vendor/custom.modernizr.js")]
         [:body.row
          [:nav.top-bar
           [:ul.title-area
            [:li.name [:h1 [:a {:href "/"} "jí"]]]
            [:li.toggle-topbar.menu-icon [:a {:href "#"} [:span]]]]
           [:section.top-bar-section
            [:ul.left
             [:li.divider]
             [:li [:a {:href "/games"} "All Games"]]]]]
          [:div#messages.large-12.columns]
          [:div#content.large-12.columns
           ~@(:content blocks)]
          [:script "document.write('<script src=/js/vendor/' + ('__proto__' in {} ? 'zepto' : 'jquery') + '.js><\\/script>');"]
          (page/include-js "/js/foundation.min.js") ;; TODO include individually
          [:script "$(document).foundation();"]
          ~@(:body-end blocks)]))))

(defn game-url
  ([] (url "/games"))
  ([id] (url "/games/" id)))

(defpage error [message]
  (defblock content
    [:div.alert-box.alert message]))

(defpage game [game-env]
  (defblock content
    [:div#game.row.collapse {:data-game-id (:id game-env)}])
  (defblock body-end
    (page/include-js "/js/main.js")
    [:script "$(ji.main.init);"]))


(defpage game-create [game-id]
  (defblock content
    [:form
     {:method "POST"
      :action (game-url)}
     [:input {:type "hidden" :name "game-id" :value game-id}]
     [:div.row
      [:div.large-6.small-12.columns.large-centered
       [:h2 "Game does not exist ..yet!"]
       [:input.button
        {:type "submit"
         :value "Create Game"}]]]]))

(defn lobby-row [{:keys [id game clients max-clients] :as game-env}]
  (let [num-clients (count clients)]
    [:tr
     [:td id]
     [:td.players (format "%d/%d" num-clients max-clients)]
     [:td (when (< num-clients max-clients) [:a.button {:href (game-url id)} "Join"])]]))

(defpage lobby [game-envs]
  (defblock content
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
                 (lobby-row @game-env))]]]]))
