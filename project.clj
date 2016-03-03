(defproject bird-wave "0.1.0-SNAPSHOT"
  :description "Watch bird migrations"
  :url "http://birdwave.neo.com"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [io.pedestal/pedestal.service "0.2.2"]
                 [io.pedestal/pedestal.service-tools "0.2.2"]
                 [com.datomic/datomic-pro "0.9.4815.12"
                  :exclusions [org.slf4j/slf4j-nop org.slf4j/slf4j-api]]
                 [enlive "1.1.5"]
                 [com.cognitect/transit-clj "0.8.255"]

                 [org.clojure/clojurescript "0.0-2740"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [com.cognitect/transit-cljs "0.8.188"]
                 [org.omcljs/om "0.8.7" :as om]
                 [cljsjs/d3 "3.5.3-0"]
                 [com.cemerick/url "0.1.1"]
                 [cascalog/cascalog-core "3.0.0"]
                 [cascalog/cascalog-more-taps "3.0.0"]]
  :plugins      [[lein-cljsbuild "1.0.3"]
                 [datomic-schema-grapher "0.0.1"]
                 [ohpauleez/lein-pedestal "0.1.0-beta10"]]
  :source-paths ["src/clj"]

  :cljsbuild
    {:builds
     {:dev
      {:source-paths ["src/cljs" "dev/cljs"]
       :compiler
       {:output-to "resources/public/javascript/build/client-dev.js"
        :output-dir "resources/public/javascript/build"
        :source-map "resources/public/javascript/build/client-dev.js.map"
        :optimizations :whitespace
        :pretty-print true
        :preamble ["vendor/scroll_into_view_polyfill.js"
                   "vendor/topojson.js"
                   "vendor/colorbrewer.js"
                   "vendor/enquire.js"]
        :externs ["externs/topojson.js"
                  "externs/colorbrewer.js"
                  "externs/ga.js"
                  "externs/enquire.js"]}}
      :advanced
      {:source-paths ["src/cljs"]
       :compiler
       {:output-to "resources/public/javascript/advanced/client.js"
        :output-dir "resources/public/javascript/advanced"
        :source-map "resources/public/javascript/advanced/client.js.map"
        :optimizations :advanced
        :pretty-print false
        :preamble ["vendor/scroll_into_view_polyfill.js"
                   "vendor/topojson.js"
                   "vendor/colorbrewer.js"
                   "vendor/enquire.js"]
        :externs ["externs/topojson.js"
                  "externs/colorbrewer.js"
                  "externs/ga.js"
                  "externs/enquire.js"]}}
      :prod
      {:source-paths ["src/cljs"]
       :compiler
       {:output-to "resources/public/javascript/client.js"
        :optimizations :advanced
        :pretty-print false
        :preamble ["vendor/scroll_into_view_polyfill.js"
                   "vendor/topojson.js"
                   "vendor/colorbrewer.js"
                   "vendor/enquire.js"]
        :externs ["externs/topojson.js"
                  "externs/colorbrewer.js"
                  "externs/ga.js"
                  "externs/enquire.js"]}}}}

  :min-lein-version "2.0.0"
  :resource-paths ["config", "resources"]
  :aliases {"run-dev" ["trampoline" "run" "-m" "bird-wave.server/run-dev"]}
  :profiles {:dev {:dependencies [[io.pedestal/pedestal.jetty "0.2.2"]
                                  [datomic-schema-grapher "0.0.1"]
                                  [ankha "0.1.1"]
                                  [org.clojure/tools.namespace "0.2.4"]
                                  [org.apache.hadoop/hadoop-core "1.2.1"]]
                   :jvm-opts ["-Xms768m" "-Xmx768m"]
                   :source-paths ["dev/clj"]}}



  :pedestal {:server-ns "bird-wave.server"
             :url-pattern "/*"})
