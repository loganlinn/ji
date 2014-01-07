(ns ji.ui.status
  "UI component for displaying game status, suich as last set & cards left"
  (:require [ji.ui :as ui]
            [ji.domain.game :as game]
            [ji.domain.messages :as msg]
            [ji.ui.card :as card]
            [ji.util.helpers :refer [event-chan]]
            [clojure.set :as s]
            [cljs.core.async :as async
             :refer [<! >! chan close! put! timeout]]
            [cljs.core.match]
            [dommy.core :as dom])
  (:require-macros
    [dommy.macros :refer [sel sel1 deftemplate node]]
    [cljs.core.async.macros :refer [go alt!]]
    [cljs.core.match.macros :refer [match]]))

(deftemplate status-tmpl [last-set cards-remaining]
  [:div.row.collapse
   [:div.cards-remaining.columns
    [:h5.subheader.hide-for-small
     "Cards Remaining: " [:span (or cards-remaining "?")]]
    [:h5.subheader.show-for-small
     [:small "Cards Remaining: " [:span (or cards-remaining "?")]]]]
   (if last-set
     [:div.last-set.columns
      [:h5.subheader.hide-for-small "Last Set: " [:span (:player-id last-set)]]
      [:h5.subheader.show-for-small [:small "Last Set: " [:span (:player-id last-set)]]]
      [:div.row.collapse
       [:div.large-6.large-centered.columns (card/set-tmpl (:cards last-set))]]]) ])

(defn go-status-ui [container ch]
  (go (loop []
        (match (<! ch)
          nil
          (dom/set-html! container "")

          ;; TODO actual dynamic templates (fields)
          {:sets sets :cards-remaining cards-remaining}
          (let [status-el (status-tmpl (last sets) cards-remaining)]
            (dom/replace-contents! container status-el)
            (recur))

          :else (recur)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;; Records

(defrecord StatusComponent [ch]
  ui/IComponent
  (attach! [component container]
    (let [status-el (status-tmpl nil nil)]
      (dom/replace-contents! container status-el)
      (go-status-ui container ch)))
  (destroy! [component container exit-data]
    (dom/set-html! container "")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;; Public

(defn create [ch]
  (->StatusComponent ch))
