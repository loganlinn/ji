(ns ji.system
  (:gen-class)
  (:require [ji.service :as service]
            [taoensso.timbre :refer [debug info]]
            [environ.core :refer [env]]
            [clojure.core.async :refer [chan]]
            [com.keminglabs.jetty7-websockets-async.core :as ws]
            [ring.adapter.jetty :refer [run-jetty]]))

(defn system []
  {:game-envs (atom {})
   :client-chan (chan)
   :port (Integer/parseInt (:port env "8080"))})

(defn start [{:keys [game-envs client-chan] :as system}]
  (info "Starting system...")
  (service/register-ws-app! game-envs client-chan)
  (assoc system :server
         (run-jetty (service/create-app game-envs)
                    {:join? false :port (:port system)
                     :configurator (ws/configurator client-chan
                                                    {:path "/games"})})))

(defn stop [system]
  (info "Stopping system...")
  (when-let [server (:server system)]
    (.stop server))
  (dissoc system :server))
