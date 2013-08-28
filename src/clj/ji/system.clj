(ns ji.system
  (:require [ji.service :as service]
            [clojure.core.async :refer [chan]]
            [com.keminglabs.jetty7-websockets-async.core :as ws]
            [ring.adapter.jetty :refer [run-jetty]]))

(defn system []
  {:connection-chan (chan)
   :port 8080})

(defn start [system]
  (service/register-ws-app! (:connection-chan system))
  (assoc system :server
         (run-jetty service/app
                    {:join? false :port (:port system)
                     :configurator (ws/configurator (:connection-chan system))})))

(defn stop [system]
  (when-let [server (:server system)]
    (.stop server))
  (dissoc system :server))
