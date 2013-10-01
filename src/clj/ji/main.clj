(ns ji.main
  (:gen-class)
  (:require [ji.system :as system]))

(defn -main
  [& args]
  (system/start (system/system)))
