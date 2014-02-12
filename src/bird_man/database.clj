(ns bird-man.database
  (:require [bird-man.import :as import]
            [datomic.api :as d :refer (db q)]
            [clojure.tools.trace :refer (deftrace trace)]))

(defn existing-taxons [db taxons]
  (into {}
        (q '[:find ?taxonomic-order ?taxon
             :in $ [?taxonomic-order]
             :where
             [?taxon :taxon/order ?order]]
           db
           (map #(vector (:taxon/order %)) taxons))))

(defn transact-rows [conn pairs]
  (let [taxons (existing-taxons (db conn) (map first pairs))
        tx-data (for [[taxon sighting] pairs]
                  (let [taxon-id (get taxons (:taxon/order taxon))]
                    (if taxon-id
                      [(assoc sighting
                         :db/id (d/tempid :db.part/user)
                         :sighting/taxon taxon-id)]
                      (let [tmp-taxon-id (d/tempid :db.part/user)]
                        [(assoc taxon
                           :db/id tmp-taxon-id)
                         (assoc sighting
                           :db/id (d/tempid :db.part/user)
                           :sighting/taxon tmp-taxon-id)]))))]
    (d/transact conn (flatten tx-data))))

(defn seed-rows [conn {:keys [seed-file batch-size]}]
  (loop [batch (partition-all batch-size (import/seed-data seed-file)) count 0]
    (let [b (first batch) more (next batch)]
      (transact-rows conn b)
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
      @(d/transact conn (read-string (slurp "resources/schema.edn")))
      (if seed-file
        (seed-rows conn config)))
    conn))
