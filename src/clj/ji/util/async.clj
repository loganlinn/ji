(ns ji.util.async
  (:require [clojure.core.async :as async
             :refer [<! >! <!! >!! timeout chan alt! alts!! go close!]]))

(defn map-source
  ([f source] (map-source (chan) f source))
  ([c f source]
   (go
     (loop [k (<! source)]
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

(defn test-chan
  []
  (let [c-src (chan)
        x (chan)]
    (go
      (loop [cs []]
        (when-let [c (<! c-src)]
          (println "NEW CHANNEL!" c cs)
          (recur (conj cs c)))))
    (>!! c-src (chan))
    (>!! c-src (chan))
    (>!! c-src (chan))
    (close! c-src)
    ))

(comment
  (test-chan))
