(ns ji.system
  (:require [ji.service :as service]
            [clojure.core.async :refer [chan]]
            [com.keminglabs.jetty7-websockets-async.core :as ws]
            [ring.adapter.jetty :refer [run-jetty]]))

(defn system []
  {:game-envs (atom {})
   :client-chan (chan)
   :port 8080})

(defn start [{:keys [game-envs client-chan] :as system}]
  (service/register-ws-app! game-envs client-chan)
  (assoc system :server
         (run-jetty service/app
                    {:join? false :port (:port system)
                     :configurator (ws/configurator client-chan)})))

(defn stop [system]
  (when-let [server (:server system)]
    (.stop server))
  (dissoc system :server))
