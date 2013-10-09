(ns ji.ui.card
  (:require [dommy.core :as dom])
  (:require-macros
    [dommy.macros :refer [sel1 deftemplate]]))

;; TODO obfuscation!

(def dict {:solid "f" :striped "s" :outlined "e"
           :oval "o" :squiggle "s" :diamond "d"
           :red "r" :green "g" :purple "b"})

(defn card-src [{:keys [shape color number fill]}]
  (str "/cards/" number (dict fill) (dict color) (dict shape) ".png"))

(defn set-id [cards] (-> cards set hash))
(defn card-id [card] (hash card))

(defn is-card-elem? [card elem]
  (=  (card-id card) (dom/attr elem "data-card-id")))

(deftemplate card-tmpl [card]
  [:span.card {:data-card-id (card-id card)}
   [:img {:src (card-src card) :alt ""}]])

(deftemplate set-tmpl [cards]
  [:ul.set
   {:data-set-id (set-id cards)}
   (for [card cards] [:li (card-tmpl card)])])
