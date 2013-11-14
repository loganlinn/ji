(ns ji.ui
  (:require [cljs.core.async :as async
             :refer [<! >! chan close! put! timeout]])
  (:require-macros
    [cljs.core.async.macros :refer [go alt!]]))

(defprotocol IComponent
  (attach! [component container])
  (destroy! [component container exit-data]))

(defn run-component! [component container]
  (let [run-chan (attach! component container)]
    (go
      (let [exit-msg (<! run-chan)]
        (destroy! component container exit-msg)))))
