(defproject ji "0.1.0-SNAPSHOT"
  :description "Set! game clone"
  :url "github.com/loganlinn/ji"
  :license {:name "Eclipse Public License" :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/clojurescript "0.0-1859"]
                 [org.clojure/core.async "0.1.0-SNAPSHOT"]
                 [org.clojure/tools.reader "0.7.6"]
                 [org.clojure/core.match "0.2.0-rc5"]
                 [ring/ring-jetty-adapter "1.2.0"]
                 [compojure "1.1.5" :exclusions [ring/ring-core]]
                 [com.keminglabs/jetty7-websockets-async "0.1.0-SNAPSHOT"]
                 [prismatic/dommy "0.1.1"]]
  :repositories {"sonatype-oss-public" "https://oss.sonatype.org/content/groups/public/"}
  :min-lein-version "2.0.0"

  :profiles {:dev {:source-paths ["dev"]
                   :dependencies [[org.clojure/tools.namespace "0.2.4"]
                                  [org.clojure/java.classpath "0.2.0"]
                                  [midje "1.5.1"]]}}

  :source-paths ["src/clj" "src/cljs"]
  :test-paths ["test/clj"]

  :plugins [[lein-cljsbuild "0.3.2"]]
  :cljsbuild {:builds {:dev
                       {:source-paths ["src/cljs"]
                        :compiler {:optimizations :whitespace
                                   :pretty-print true
                                   :output-to "out/public/js/main.js"}}
                       :prod
                       {:source-paths ["src/cljs"]
                        :compiler {:optimizations :advanced
                                   :pretty-print false
                                   :output-to "out/public/js/main.js"}}}
              :crossovers [ji.domain]})
