(ns ji.ui.card
  (:require-macros
    [dommy.macros :refer [deftemplate]]))

(let [m {:solid "f" :striped "s" :outlined "e"
         :oval "o" :squiggle "s" :diamond "d"
         :red "r" :green "g" :purple "b"}]
  (deftemplate card-tmpl [{:keys [shape color number fill] :as card}]
    [:span.card
     [:img
      {:src (str "/cards/" number (m fill) (m color) (m shape) ".png")
       :alt ""}]]))

