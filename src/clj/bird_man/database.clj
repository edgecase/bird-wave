(ns bird-man.database
  (:require [bird-man.import :as import]
            [datomic.api :as d :refer (db q)]
            [clojure.string :as cs]
            [clojure.set :as cset]
            ;; [clojure.tools.trace :refer (deftrace trace)]
            ))

(defn partition-name
  "Return partition name as keyword given a taxonomic order string"
  [order]
  (keyword (str "taxon/order-"
                (cs/trim (cs/replace order #"\." "-")))))

(defn existing-taxons
  "Return a map from taxonomic order to :db/id"
  [db taxons]
  (into {}
        (q '[:find ?taxonomic-order ?taxon
             :in $ [?taxonomic-order]
             :where
             [?taxon :taxon/order ?order]]
           db
           (map #(vector (:taxon/order %)) taxons))))

(defn transact-rows [conn pairs]
  (let [taxons (map first pairs)
        existing-taxons (existing-taxons (db conn) taxons)
        partitions-to-create (cset/difference (set (map :taxon/order taxons))
                                              (set (keys existing-taxons)))
        new-partitions-tx (for [order partitions-to-create]
                            {:db/id (d/tempid :db.part/db)
                             :db/ident (partition-name order)
                             :db.install/_partition :db.part/db})
        tx-data (for [[taxon sighting] pairs]
                   (let [taxon-id (get existing-taxons (:taxon/order taxon))]
                     (if taxon-id
                       [(assoc sighting
                          :db/id (d/tempid (d/part taxon-id))
                          :sighting/taxon taxon-id)]
                       (let [part (partition-name (:taxon/order taxon))
                             tmp-taxon-id (d/tempid part)]
                         [(assoc taxon
                            :db/id tmp-taxon-id)
                          (assoc sighting
                            :db/id (d/tempid part)
                            :sighting/taxon tmp-taxon-id)]))))]
    (when (not-empty new-partitions-tx)
      @(d/transact conn new-partitions-tx))
    @(d/transact conn (flatten tx-data))))

(defn seed-rows [conn {:keys [seed-file batch-size skip-rows nth-row]}]
  (loop [batch (partition-all batch-size (import/seed-data seed-file skip-rows nth-row)) count 0]
    (let [b (first batch) more (next batch)]
      (try
        (transact-rows conn b)
        (catch Throwable t
          (println "exception caught" t)
          (println "retrying after 10s")
          (Thread/sleep 10000)
          (transact-rows conn b)))
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
