(ns bird-man.database
  (:require [bird-man.import :as import]
            [datomic.api :as d :refer (db q)]))

(defn seed-rows [conn batch-size]
  (loop [batch (partition-all batch-size import/sample-seed-data) count 0]
    (let [b (first batch) more (next batch)]
      (d/transact conn b)
      (prn count)
      (when more
        (recur (next batch) (inc count))))))

(defn init
  "Creates the datomic database and loads seed data"
  [url seed]
  (if seed
    (let [db-created? (d/create-database url)
          conn (d/connect url)]
      (when db-created?
        (prn "db was created. reading schema")
        (d/transact conn (read-string (slurp "resources/schema.edn")))
        (seed-rows conn 1000)
        conn))
    (let [conn (d/connect url)]
      (prn conn)
      conn)))
