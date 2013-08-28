(ns ji.websocket
  (:require [cljs.reader :refer [read-string]]
            [cljs.core.async :as async :refer [<! >! chan close! put! take! sliding-buffer dropping-buffer timeout]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn connect!
  "Creates websocket to "
  ([uri] (connect! uri {}))
  ([uri {:keys [in out]
                         :or {in chan out chan}}]
   (let [on-connect (chan)]
     (go
       (let [in (in)
             out (out)
             ws (js/WebSocket. uri)]
         (doto ws
           (aset "onopen"
                 (fn []
                   (close! on-connect)
                   (go (loop []
                         (let [data (<! in)]
                           (if-not (nil? data)
                             (do (.send ws (pr-str data))
                                 (recur))
                             (do (close! out)
                                 (.close ws))))))))
           (aset "onmessage"
                 (fn [m]
                   (put! out (read-string (.-data m)))))
           (aset "onclose"
                 (fn []
                   (close! in)
                   (close! out))))

         (<! on-connect)
         {:uri uri :conn ws :in in :out out})))))
