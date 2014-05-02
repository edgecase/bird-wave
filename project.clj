(defproject bird-man "0.1.0-SNAPSHOT"
  :description "Watch bird migrations"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [io.pedestal/pedestal.service "0.2.2"]
                 [io.pedestal/pedestal.service-tools "0.2.2"]
                 [com.datomic/datomic-pro "0.9.4609" :exclusions [org.slf4j/slf4j-nop]]
                 [enlive "1.1.5"]
                 [org.clojure/clojurescript "0.0-2202"]
                 [org.clojure/core.async "0.1.301.0-deb34a-alpha"]
                 [om "0.6.2"]
                 [secretary "1.1.0"]]
  :git-dependencies [["https://github.com/arosequist/om-autocomplete.git"]]
  :plugins      [[lein-git-deps "0.0.1-SNAPSHOT"]
                 [lein-cljsbuild "1.0.3"]
                 [datomic-schema-grapher "0.0.1"]
                 [ohpauleez/lein-pedestal "0.1.0-beta10"]]
  :source-paths ["src/clj"]

  :cljsbuild
    {:builds
     {:dev
      {:source-paths ["src/cljs" "dev/cljs" ".lein-git-deps/om-autocomplete/src"]
       :compiler
       {:output-to "resources/public/javascript/client-dev.js"
        :optimizations :whitespace
        :pretty-print true
        :preamble ["react/react.js"]
        :externs ["react/externs/react.js" "externs/d3_externs_min.js" "externs/topojson.js"]}}
      :prod
      {:source-paths ["src/cljs"  ".lein-git-deps/om-autocomplete/src"]
       :compiler
       {:output-to "resources/public/javascript/client.js"
        :optimizations :advanced
        :pretty-print false
        :preamble ["react/react.min.js"]
        :externs ["react/externs/react.js"
                  "externs/d3_externs_min.js"
                  "externs/topojson.js"
                  "externs/colorbrewer.js"]}}}}

  :min-lein-version "2.0.0"
  :resource-paths ["config", "resources"]
  :aliases {"run-dev" ["trampoline" "run" "-m" "bird-man.server/run-dev"]}
  :profiles {:dev {:dependencies [[io.pedestal/pedestal.jetty "0.2.2"]
                                  [datomic-schema-grapher "0.0.1"]
                                  [ankha "0.1.1"]
                                  [org.clojure/tools.namespace "0.2.4"]]
                   :source-paths ["dev/clj"]}}



  :pedestal {:server-ns "bird-man.server"
             :url-pattern "/*"})
