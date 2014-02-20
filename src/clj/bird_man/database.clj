(ns bird-man.database
  (:require [bird-man.import :as import]
            [datomic.api :as d :refer (db q)]
            [clojure.string :as cs]
            [clojure.set :as cset]
            ;;[clojure.tools.trace :refer (deftrace trace)]
            ))

(defn partition-name
  "Return partition name as keyword given a taxonomic order string.
   Sightings are typically queried by species, so that is how we partition."
  [order]
  (keyword (str "taxon/order-"
                (cs/trim (cs/replace order #"\." "-")))))

(def taxon-partition
  "Taxonomy information is queried for the index page so we put all
   in the same partition."
  :taxonomy)

(defn existing-taxons
  "Return a map from taxonomic order to :db/id"
  [db taxons]
  (into {}
        (q '[:find ?order ?taxon
             :in $ [?order]
             :where
             [?taxon :taxon/order ?order]]
           db
           (map #(vector (:taxon/order %)) taxons))))

(defn existing-partitions
  "Return a set of all current partitions"
  [db]
  (set (map first (q '[:find ?part
                       :where
                       [?e :db.install/partition ?p]
                       [?p :db/ident ?part]]
                     db))))

(defn transact-rows [conn pairs]
  (let [database (db conn)
        taxons (map first pairs)
        existing-taxons (existing-taxons database taxons)
        existing-partitions (existing-partitions database)
        needed-partitions (set (map partition-name (map :taxon/order taxons)))
        diff (cset/difference needed-partitions existing-partitions)
        new-partitions-tx (for [part diff]
                            {:db/id (d/tempid :db.part/db)
                             :db/ident part
                             :db.install/_partition :db.part/db})
        tx-data (for [[taxon sighting] pairs]
                   (let [taxon-id (get existing-taxons (:taxon/order taxon))]
                     (if taxon-id
                       [(assoc sighting
                          :db/id (d/tempid (d/part taxon-id))
                          :sighting/taxon taxon-id)]
                       (let [tmp-taxon-id (d/tempid taxon-partition)]
                         [(assoc taxon
                            :db/id tmp-taxon-id)
                          (assoc sighting
                            :db/id (d/tempid (partition-name (:taxon/order taxon)))
                            :sighting/taxon tmp-taxon-id)]))))]
    (when (not-empty new-partitions-tx)
      (println "Creating " (count new-partitions-tx) "new partitions")
      @(d/transact conn new-partitions-tx))
    @(d/transact conn (flatten tx-data))))

(defn seed-rows [conn {:keys [seed-file batch-size skip-rows nth-row]}]
  (loop [batch (partition-all batch-size (import/seed-data seed-file skip-rows nth-row)) count 0]
    (let [b (first batch) more (next batch)]
      (try
        (transact-rows conn b)
        (catch Throwable t
          (println "exception caught" t)
          (println "retrying after 20s")
          (Thread/sleep 20000)
          (transact-rows conn b)))
      (println "Imported" (+ skip-rows (* batch-size (inc count))) "lines")
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
