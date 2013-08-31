(ns ji.websocket
  (:require [cljs.reader :refer [read-string]]
            [cljs.core.async :as async :refer [<! >! chan close! put! take! sliding-buffer dropping-buffer timeout]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn connect!
  "Connects to a websocket. Returns a channel that, when connected, puts a
  map with with keys,
  :uri  The URI connected to
  :ws   Raw Websocket object
  :in   Channel to write values to socket on
  :out  Channel to recieve socket data on"
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
         ;; swap in/out for more intuitive naming for receiver
         {:uri uri :ws ws :out in :in out})))))
