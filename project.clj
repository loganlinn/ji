(defproject ji "0.1.0-SNAPSHOT"
  :description "Set! game clone"
  :url "github.com/loganlinn/ji"
  :license {:name "Eclipse Public License" :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/clojurescript "0.0-1909"]
                 [org.clojure/core.async "0.1.242.0-44b1e3-alpha"]
                 [org.clojure/tools.reader "0.7.9"]
                 [org.clojure/core.match "0.2.0"]
                 [com.taoensso/timbre "2.6.2"]
                 [environ "0.4.0"]
                 [hiccup "1.0.4"]
                 [ring/ring-jetty-adapter "1.2.0"]
                 [compojure "1.1.5" :exclusions [ring/ring-core]]
                 [com.keminglabs/jetty7-websockets-async "0.1.0"]
                 [prismatic/dommy "0.1.2"]]
  :repositories {"sonatype-oss-public" "https://oss.sonatype.org/content/groups/public/"}
  :min-lein-version "2.0.0"

  :uberjar-name "ji-standalone.jar"
  :aot [ji.main]

  :profiles {:dev {:source-paths ["dev"]
                   :dependencies [[org.clojure/tools.namespace "0.2.4"]
                                  [org.clojure/java.classpath "0.2.0"]
                                  [midje "1.5.1"]]
                   :plugins [[com.cemerick/austin "0.1.1"]]
                   :env {:prod false}}
             :prod {:env {:prod true}}}

  :source-paths ["src/clj" "src/cljs"]
  :test-paths ["test/clj"]

  :plugins [[lein-cljsbuild "0.3.2"]
            [lein-environ "0.4.0"]]
  :cljsbuild {:builds {:dev
                       {:source-paths ["src/cljs"]
                        :compiler {:optimizations :whitespace
                                   :pretty-print true
                                   :static-fns true
                                   :output-to "resources/public/js/main.js"
                                   ;:source-map "resources/public/js/main.js.map"
                                   }}
                       :prod
                       {:source-paths ["src/cljs"]
                        :compiler {:optimizations :advanced
                                   :pretty-print false
                                   :static-fns true
                                   :output-to "resources/public/js/main.js"
                                   ;:source-map "resources/public/js/main.js.map"
                                   }}}
              :crossovers [ji.domain]})
