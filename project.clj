(defproject ji "0.1.0-SNAPSHOT"
  :description "Set! game clone"
  :url "github.com/loganlinn/ji"
  :license {:name "Eclipse Public License" :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/clojurescript "0.0-2138"]
                 [org.clojure/core.async "0.1.267.0-0d7780-alpha"]
                 [org.clojure/tools.reader "0.8.3"]
                 [org.clojure/core.match "0.2.0"]
                 [com.taoensso/timbre "2.6.2"]
                 [environ "0.4.0"]
                 [hiccup "1.0.4"]
                 [ring/ring-jetty-adapter "1.2.0"]
                 [compojure "1.1.5" :exclusions [ring/ring-core]]
                 [com.keminglabs/jetty7-websockets-async "0.1.0"]
                 [prismatic/dommy "0.1.2"]
                 ;[prismatic/schema "0.1.10"]
                 [rident "0.1.0"]]
  :repositories {"sonatype-oss-public" "https://oss.sonatype.org/content/groups/public/"}
  :min-lein-version "2.0.0"

  :uberjar-name "ji-standalone.jar"
  :aot [ji.main]

  :plugins [[lein-cljsbuild "1.0.0-alpha2"]
            [lein-environ "0.4.0"]
            [com.keminglabs/cljx "0.3.1"]]
  :hooks [cljx.hooks]
  :profiles {:dev {:source-paths ["dev"]
                   :dependencies [[org.clojure/tools.namespace "0.2.4"]
                                  [org.clojure/java.classpath "0.2.0"]
                                  [midje "1.5.1"]]
                   :plugins [[com.cemerick/austin "0.1.1"]]
                   :env {:prod false}}
             :prod {:env {:prod true}}}

  :source-paths ["target/generated/src/clj" "src/clj"]
  :test-paths ["test/clj"]

  :cljx {:builds [{:source-paths ["src/cljx"]
                   :output-path "target/generated/src/clj"
                   :rules :clj}
                  {:source-paths ["src/cljx"]
                   :output-path "target/generated/src/cljs"
                   :rules :cljs}
                  {:source-paths ["test/cljx"]
                   :output-path "target/generated/test/clj"
                   :rules :clj}
                  {:source-paths ["test/cljx"]
                   :output-path "target/generated/test/cljs"
                   :rules :cljs}]}
  :cljsbuild {:builds {:dev
                       {:source-paths ["target/generated/src/cljs" "src/cljs"]
                        :debug true
                        :compiler {:optimizations :whitespace
                                   :debug true
                                   :pretty-print true
                                   :static-fns true
                                   :output-to "resources/public/js/main.js"
                                   ;:source-map "resources/public/js/main.js.map"
                                   }}
                       :prod
                       {:source-paths ["target/generated/src/cljs" "src/cljs"]
                        :compiler {:optimizations :advanced
                                   :pretty-print false
                                   :static-fns true
                                   :output-to "resources/public/js/main.js"
                                   ;:source-map "resources/public/js/main.js.map"
                                   }}}})
