(ns ji.service
  (:require [com.keminglabs.jetty7-websockets-async.core :as ws]
            [clojure.core.async :refer [go <! >!]]
            [clojure.core.match :refer [match]]
            [compojure.core :refer [routes]]
            [compojure.route :as route]))

(def app
  (routes
    (route/files "/" {:root "public"})))

(defn register-ws-app!
  [conn-chan]
  (go
    (while true
      (let [conn (<! conn-chan)]
        (println conn)
        (match [conn]
               [{:uri uri :in in :out out}]
               (go
                 (>! in "Yo")
                 (loop []
                   (when-let [msg (<! out)]
                     (prn msg)
                     (recur))))))
      )))
