(defproject bird-man "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [io.pedestal/pedestal.service "0.2.2"]
                 [io.pedestal/pedestal.service-tools "0.2.2"]
                 [hiccup "1.0.5"]
                 [com.datomic/datomic-pro "0.9.4609" :exclusions [org.slf4j/slf4j-nop]]
                 [org.clojure/tools.namespace "0.2.4"]
                 [enlive "1.1.4"]
                 [com.novemberain/validateur "1.5.0"]
                 [criterium "0.4.3"]
                 [org.clojure/clojurescript "0.0-2173"]
                 [om "0.5.2"]
                 [secretary "1.0.1"]
                 [datomic-schema-grapher "0.0.1"]
                 [ankha "0.1.1"]]
  :plugins      [[lein-cljsbuild "1.0.2"]
                 [datomic-schema-grapher "0.0.1"]
                 [ohpauleez/lein-pedestal "0.1.0-beta10"]]
  :source-paths ["src/clj"]
  :hooks [leiningen.cljsbuild]
  :cljsbuild
    {:builds
     {:dev
      {:source-paths ["src/cljs"]
       :compiler
       {:output-to "resources/public/javascript/client-dev.js"
        :optimizations :whitespace
        :pretty-print true
        :preamble ["react/react.js"]
        :externs ["react/externs/react.js" "externs/d3_externs_min.js" "externs/topojson.js"]}}}}

  :min-lein-version "2.0.0"
  :resource-paths ["config", "resources"]
  :aliases {"run-dev" ["trampoline" "run" "-m" "bird-man.server/run-dev"]}
  :profiles {:dev {:dependencies [[io.pedestal/pedestal.jetty "0.2.2"]]
                   :source-paths ["dev"]}}

  :pedestal {:server-ns "bird-man.server"
             :url-pattern "/*"})
