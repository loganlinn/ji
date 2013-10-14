(ns ji.util.helpers
  (:require
    [cljs.core.async :as async
     :refer [<! >! chan close! sliding-buffer dropping-buffer
             put! timeout]]
    [dommy.core :as dom]
    [dommy.template]
    [goog.net.Jsonp]
    [goog.Uri])
  (:require-macros
    [cljs.core.async.macros :as m :refer [go alt!]]
    [dommy.macros :refer [sel sel1]]))

(defn clear! ;; Remove when dommy "0.1.2" released
  "clears all children from `elem`"
  [elem]
  (set! (.-innerHTML (dommy.template/->node-like elem)) ""))

;; =============================================================================
;; Printing

(defn js-print [& args]
  (if (js* "typeof console != 'undefined'")
    (.log js/console (apply str args))
    (js/print (apply str args))))

(set! *print-fn* js-print)

;; =============================================================================
;; Pattern matching support

;(extend-type object
  ;ILookup
  ;(-lookup [coll k]
    ;(-lookup coll k nil))
  ;(-lookup [coll k not-found]
    ;(if (.hasOwnProperty coll k)
      ;(aget coll k)
      ;not-found)))

;; =============================================================================
;; Channels

(defn put-all! [cs x]
  (doseq [c cs]
    (put! c x)))

(defn event-chan
  ([type] (event-chan (sel1 :window)))
  ([el type] (event-chan (chan) el type))
  ([c el type]
   (let [writer #(put! c %)]
     (dom/listen! el type writer)
     {:chan c
      :unsubscribe #(dom/unlisten! el type writer)})))
