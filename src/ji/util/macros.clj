(ns ji.util.macros)

(defmacro go-loop [& body]
  (if (vector? (first body))
    `(cljs.core.async.macros/go
       (while true
         ~(cons `let body)))
    `(cljs.core.async.macros/go
       (while true
         ~@body))))
