(ns ji.util.async
  (:require [clojure.core.async :as async
             :refer [<! >! <!! >!! timeout chan alt! alts!! go close!]]
            [ji.util.macros :refer [go-loop]]))

(defn map-source
  ([f source] (map-source (chan) f source))
  ([c f source]
   (go
     (loop [k (<! source)]
       (println "source-read" (pr-str k))
       (if-let [v (f k)]
         (do
           (>! c v)
           (recur (<! source)))
         (close! c))))
   c))

(defn map-sink
  ([f sink] (map-sink (chan) f sink))
  ([c f sink]
   (go
     (loop []
       (if-let [v (f (<! c))]
         (do
           (>! sink v)
           (recur))
         (close! c))))
   c))
