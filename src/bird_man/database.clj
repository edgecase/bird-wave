(ns bird-man.database
  (:require [bird-man.import :as import]
            [datomic.api :as d :refer (db q)]))

(defn seed-rows [conn {:keys [seed-file batch-size]}]
  (loop [batch (partition-all batch-size (import/seed-data seed-file)) count 0]
    (let [b (first batch) more (next batch)]
      (d/transact conn b)
      (println "Imported" (* batch-size (inc count)) "lines")
      (when more
        (recur more (inc count))))))

(defn init
  "Returns a datomic connection.
   If the database didn't already exist it is created
   and seed data is loaded from seed-file"
  [{:keys [url seed-file] :as config}]
  (let [db-created? (d/create-database url)
        conn (d/connect url)]
    (when db-created?
      (prn "db was created. reading schema")
      (d/transact conn (read-string (slurp "resources/schema.edn")))
      (if seed-file
        (seed-rows conn config)))
    conn))
