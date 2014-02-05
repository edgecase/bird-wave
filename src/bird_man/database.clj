(ns bird-man.database
  (:require [bird-man.import :as import]
            [datomic.api :as d :refer (db q)]))

(defn seed-rows [conn batch-size]
  (doseq [batch (partition-all batch-size import/sample-seed-data)]
    (pr (str "commiting " (count batch) " records..."))
    (d/transact conn batch)
    (prn "done.")))

(defn init
  "Creates the datomic database and loads seed data"
  [url]
  (let [db-created? (d/create-database url)
        conn (d/connect url)]
    (when db-created?
      (prn "db was created. reading schema")
      (d/transact conn (read-string (slurp "resources/schema.edn")))
      (seed-rows conn 1000)
      conn)))
