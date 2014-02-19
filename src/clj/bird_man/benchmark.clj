(ns bird-man.benchmark
  (:require [criterium.core :refer :all]
            [datomic.api :as d :refer (db q)]))

(defn is-yymm? [d]
  (and (= 113 (.getYear d))
       (= 0 (.getMonth d))))

(defn old-query [db taxon]
  (q '[:find ?county ?state (sum ?count) (count ?e)
       :in $ ?taxon
       :where
       [?t :taxon/order ?taxon]
       [?e :sighting/taxon ?t]
       [?e :sighting/date ?date]
       [(bird-man.benchmark/is-yymm? ?date)]
       [?e :sighting/state-code ?state]
       [?e :sighting/count ?count]
       [?e :sighting/county ?county]]
     db
     taxon))

(defn new-query [db taxon]
  (q '[:find ?county ?state (sum ?count) (count ?e)
       :in $ ?taxon ?ym
       :where
       [?t :taxon/order ?taxon]
       [?e :sighting/taxon ?t]
       [?e :sighting/month-yr ?ym]
       [?e :sighting/state-code ?state]
       [?e :sighting/count ?count]
       [?e :sighting/county ?county]]
     db
     taxon
     "2013/01"))
