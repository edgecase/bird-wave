(defproject bird-man "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [io.pedestal/pedestal.service "0.2.2"]
                 [io.pedestal/pedestal.service-tools "0.2.2"]

                 [com.datomic/datomic-pro "0.9.4470" :exclusions [org.slf4j/slf4j-nop]]
                 [org.clojure/tools.namespace "0.2.4"]
                 [enlive "1.1.4"]
                 [io.pedestal/pedestal.jetty "0.2.2"]
                 [com.novemberain/validateur "1.5.0"]]

  :min-lein-version "2.0.0"
  :resource-paths ["config", "resources"]
  :aliases {"run-dev" ["trampoline" "run" "-m" "bird-man.server/run-dev"]}
  :profiles {:dev {:source-paths ["dev"]}})
