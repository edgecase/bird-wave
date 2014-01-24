(ns user
  (:require [clojure.java.io :as io]
            [clojure.java.javadoc :refer (javadoc)]
            [clojure.reflect :refer (reflect)]
            [clojure.string :as string]
            [clojure.pprint :refer (pprint)]
            [clojure.repl :refer :all]
            [clojure.test :as test]
            [clojure.tools.namespace.repl :refer (refresh refresh-all)]
            [io.pedestal.service-tools.dev :as pedestal-dev]
            [datomic.api :as d :refer (db q)]
            bird-man.service))

(defonce system (atom nil))

(defn init
  "Creates and initializes the system under development in the Var
  #'system."
  []
  (let [url "datomic:mem://localhost/dev"
        db-created? (d/create-database url)
        conn (d/connect url)]
  ;;  (when db-created?
   ;;   (d/transact conn (read-string (slurp "resources/schema.edn")))
    ;;  (d/transact conn (read-string (slurp "resources/seed.edn"))))
    (reset! system (merge (pedestal-dev/init bird-man.service/service #'bird-man.service/routes)
                          {:db-url url :db-conn conn}))))

(def conn (atom nil))

(defn start
  "Starts the system running, updates the Var #'system."
  []
  (reset! conn (:db-conn @system))
  (alter-var-root #'bird-man.service/datomic-connection
                  (constantly (:db-conn @system)))
  (pedestal-dev/start))

(defn stop
  "Stops the system if it is currently running, updates the Var
  #'system."
  []
  (pedestal-dev/stop)
  (d/delete-database (:db-url @system))
  (reset! system nil))

(defn go
  "Initializes and starts the system running."
  []
  (init)
  (start)
  :ready)

(defn reset
  "Stops the system, reloads modified source files, and restarts it."
  []
  (stop)
  (refresh :after 'user/go))
