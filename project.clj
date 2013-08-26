(defproject ji "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/clojurescript "0.0-1859"]
                 [org.clojure/core.match "0.2.0-rc2"]
                 [core.async "0.1.0-SNAPSHOT"]
                 [prismatic/dommy "0.1.1"]]
  :repositories {"sonatype-staging" "https://oss.sonatype.org/content/groups/staging/"}
  :cljsbuild
  {:builds [{:source-paths ["src"]
             :compiler {:optimizations :whitespace
                        :pretty-print true
                        :output-to "out/js/main.js"}}]
   :crossovers [ji.domain]})
