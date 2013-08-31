(ns ji.util.helpers
  (:require
    [cljs.core.async :as async
     :refer [<! >! chan close! sliding-buffer dropping-buffer
             put! timeout]]
    [dommy.core :as dom]
    [goog.net.Jsonp]
    [goog.Uri])
  (:require-macros
    [cljs.core.async.macros :as m :refer [go alt!]]
    [dommy.macros :refer [sel sel1]]
    [ji.util.macros :refer [go-loop]]))

;; =============================================================================
;; Printing

(defn js-print [& args]
  (if (js* "typeof console != 'undefined'")
    (.log js/console (apply str args))
    (js/print (apply str args))))

(set! *print-fn* js-print)

;; =============================================================================
;; Pattern matching support

(extend-type object
  ILookup
  (-lookup [coll k]
    (-lookup coll k nil))
  (-lookup [coll k not-found]
    (if (.hasOwnProperty coll k)
      (aget coll k)
      not-found)))

;; =============================================================================
;; Utilities

(defn now []
  (.valueOf (js/Date.)))

;; =============================================================================
;; Channels

(defn put-all! [cs x]
  (doseq [c cs]
    (put! c x)))

(defn multiplex [in cs-or-n]
  (let [cs (if (number? cs-or-n)
             (repeatedly cs-or-n chan)
             cs-or-n)]
    (go (loop []
          (let [x (<! in)]
            (if-not (nil? x)
              (do
                (put-all! cs x)
                (recur))
              :done))))
    cs))

(defn into-chan
  [out cs]
  (go (loop [cs cs]
        (if-not (empty? cs)
          (let [[v sc] (alts! cs)]
            (if-not (nil? v)
              (do
                (>! out v)
                (recur cs))
              (recur (filter #(not= sc %) cs))))
          :done)))
  out)

(defn copy-chan
  ([c]
   (first (multiplex c 1)))
  ([out c]
   (first (multiplex c [out]))))

(defn event-chan
  ([type] (event-chan (sel1 :window)))
  ([el type] (event-chan (chan) el type))
  ([c el type]
   (let [writer #(put! c %)]
     (dom/listen! el type writer)
     {:chan c
      :unsubscribe #(dom/unlisten! el type writer)})))

(defn map-chan
  ([f source] (map-chan (chan) f source))
  ([c f source]
   (go-loop
     (>! c (f (<! source))))
   c))

(defn map-source
  ([f source] (map-source (chan) f source))
  ([c f source]
   (go
     (loop []
       (if-let [v (f (<! source))]
         (do
           (>! c v)
           (recur))
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

(defn filter-chan
  ([f source] (filter-chan (chan) f source))
  ([c f source]
   (go-loop
     (let [v (<! source)]
       (when (f v)
         (>! c v))))
   c))

(defn interval-chan
  ([msecs]
   (interval-chan msecs :leading))
  ([msecs type]
   (interval-chan (chan (dropping-buffer 1)) msecs type))
  ([c msecs type]
   (condp = type
     :leading (go-loop
                (>! c (now))
                (<! (timeout msecs)))
     :falling (go-loop
                (<! (timeout msecs))
                (>! c (now))))
   c))

;; using core.match could make this nicer probably - David

(defn throttle
  ([source msecs]
   (throttle (chan) source msecs))
  ([c source msecs]
   (go
     (loop [state ::init last nil cs [source]]
       (let [[_ sync] cs]
         (let [[v sc] (alts! cs)]
           (condp = sc
             source (condp = state
                      ::init (do (>! c v)
                                 (recur ::throttling last
                                        (conj cs (timeout msecs))))
                      ::throttling (recur state v cs))
             sync (if last 
                    (do (>! c last)
                        (recur state nil
                               (conj (pop cs) (timeout msecs))))
                    (recur ::init last (pop cs))))))))
   c))

(defn debounce
  ([source msecs]
   (debounce (chan) source msecs))
  ([c source msecs]
   (go
     (loop [state ::init cs [source]]
       (let [[_ threshold] cs]
         (let [[v sc] (alts! cs)]
           (condp = sc
             source (condp = state
                      ::init
                      (do (>! c v)
                          (recur ::debouncing
                                 (conj cs (timeout msecs))))
                      ::debouncing
                      (recur state
                             (conj (pop cs) (timeout msecs))))
             threshold (recur ::init (pop cs)))))))
   c))

(defn after-last
  ([source msecs]
   (after-last (chan) source msecs))
  ([c source msecs]
   (go
     (loop [cs [source]]
       (let [[_ toc] cs]
         (let [[v sc] (alts! cs :priority true)]
           (recur
             (condp = sc
               source (conj (if toc (pop cs) cs)
                            (timeout msecs))
               toc (do (>! c (now)) (pop cs))))))))
   c))

(defn fan-in
  ([ins] (fan-in (chan) ins))
  ([c ins]
   (go (while true
         (let [[x] (alts! ins)]
           (>! c x))))
   c))

(defn distinct-chan
  ([source] (distinct-chan (chan) source))
  ([c source]
   (go
     (loop [last ::init]
       (let [v (<! source)]
         (when-not (= last v)
           (>! c v))
         (recur v))))
   c))

;(defprotocol IObservable
  ;(subscribe [c observer])
  ;(unsubscribe [c observer]))

;(defn observable [c]
  ;(let [listeners (atom #{})]
    ;(go-loop
      ;(put-all! @listeners (<! c)))
    ;(reify
      ;proto/ReadPort
      ;(take! [_ fn1-handler]
        ;(proto/take! c fn1-handler))
      ;proto/WritePort
      ;(put! [_ val fn0-handler]
        ;(proto/put! c val fn0-handler))
      ;proto/Channel
      ;(close! [chan]
        ;(proto/close! c))
      ;IObservable
      ;(subscribe [this observer]
        ;(swap! listeners conj observer)
        ;observer)
      ;(unsubscribe [this observer]
        ;(swap! listeners disj observer)
        ;observer))))

;(defn collection
  ;([] (collection (chan) (chan (sliding-buffer 1)) {}))
  ;([in events coll]
   ;(go
     ;(loop [coll coll cid 0 e nil]
       ;(when e
         ;(>! events e))
       ;(let [{:keys [op id val out]} (<! in)]
         ;(condp = op
           ;:query  (do (>! out (filter val (vals coll)))
                       ;(recur coll cid nil))
           ;:create (let [val (assoc val :id cid)]
                     ;(when out
                       ;(>! out val))
                     ;(recur (assoc coll cid val) (inc cid)
                            ;{:op :create :val val}))
           ;:read   (do (when out
                         ;(>! out (coll id)))
                       ;(recur coll cid
                              ;{:op :read :val (coll id)}))
           ;:update (recur (assoc coll id val) cid
                          ;{:op :update :prev (coll id) :val val})
           ;:delete (recur (dissoc coll id) cid
                          ;{:op :delete :val (coll id)})))))
   ;{:in in
    ;:events (observable events)}))

;(defn view
  ;([coll] (view (chan) coll))
  ;([events coll] (view events coll identity))
  ;([events coll f] (view events coll identity identity))
  ;([events coll f sort]
   ;(let [events (subscribe (:events coll) (chan))]
     ;(go
       ;(>! (:in coll) {:op :query :val f})
       ;(loop [data (<! (:out coll))]
         ;(let [[[op val] sc] (alts! [events])]
           ;(condp = sc
             ;:create (recur (assoc data (:id val) val)))))))
   ;{:events (observable events)}))
