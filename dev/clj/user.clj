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
            bird-man.service
            bird-man.database))

(defonce system (atom nil))
(defonce config (atom {:env :dev
                       :seed-file "sample_data/birds.txt"
                       :transactor :mem
                       :batch-size 1000
                       :skip-rows nil
                       :nth-row 1}))

(defn configured? []
  (:url @config))

(defn configure
  "Configure database type"
  [ & [conf]]
  (swap! config merge conf)
  (swap! config assoc :url
         (case (:transactor @config)
                 :mem "datomic:mem://birdman"
                 :dev "datomic:dev://localhost:4334/birdman"
                 :pg  "datomic:sql://birdman?jdbc:postgresql://localhost:5432/datomic?user=datomic&password=datomic"
                 (throw (ex-info "No connection string for :transactor" @config)))))

(defn init
  "Creates and initializes the system under development"
  []
  (bird-man.service/set-env (:env @config))
  (reset! system (merge (pedestal-dev/init bird-man.service/service #'bird-man.service/routes)
                        {:db-conn (bird-man.database/init @config)})))

(def conn (atom nil))

(defn start
  "Starts the system running, updates the Var #'system."
  []
  (reset! conn (:db-conn @system))
  (alter-var-root #'bird-man.service/datomic-connection
                  (constantly @conn))
  (pedestal-dev/start))

(defn stop
  "Stops the system if it is currently running, updates the Var
  #'system."
  []
  (pedestal-dev/stop)
  (when (= :mem (:transactor @config))
    (d/delete-database (:url @config)))
  (reset! system nil))

(defn go
  "Initializes and starts the system running."
  []
  (if (configured?)
    (do
      (init)
      (start)
      :ready)
    (println "Please call configure first")))

(defn reset
  "Stops the system, reloads modified source files, and restarts it."
  []
  (if (configured?)
    (let [c @config]
      (stop)
      ;; TODO: need to figure out how to preserve config data after reset -John
      ;; (configure c)
      (refresh :after 'user/go))
    (println "System is not configured")))
