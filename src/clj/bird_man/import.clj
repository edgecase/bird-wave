(ns bird-man.import
  (:require [clojure.string :as cs]
            [clojure.java.io :as io]
            [clojure.instant :as inst]
            [datomic.api :as d]))

(def fields
  ;; TODO: consider including guid as a primary key for sighting
  [:sighting/guid                   ;; "GLOBAL UNIQUE IDENTIFIER"
   :taxon/order                     ;; "TAXONOMIC ORDER"
   nil                              ;; "CATEGORY"
   :taxon/common-name               ;; "COMMON NAME"
   :taxon/scientific-name           ;; "SCIENTIFIC NAME"
   :taxon/subspecies-common-name    ;; "SUBSPECIES COMMON NAME"
   :taxon/subspecies-scientific-name;; "SUBSPECIES SCIENTIFIC NAME"
   :sighting/count                  ;; "OBSERVATION COUNT" ;; x indicates uncounted
   nil                              ;; "BREEDING BIRD ATLAS CODE"
   nil                              ;; "AGE/SEX"
   nil                              ;; "COUNTRY"
   nil                              ;; "COUNTRY_CODE"
   :sighting/state                  ;; "STATE_PROVINCE"
   :sighting/state-code             ;; "SUBNATIONAL1_CODE"
   :sighting/county                 ;; "COUNTY"
   :sighting/county-code            ;; "SUBNATIONAL2_CODE"
   nil                              ;; "IBA CODE"
   :sighting/locality               ;; "LOCALITY"
   nil                              ;; "LOCALITY ID"
   nil                              ;; "LOCALITY TYPE"
   :sighting/latitude               ;; "LATITUDE"
   :sighting/longitude              ;; "LONGITUDE"
   :sighting/date                   ;; "OBSERVATION DATE"
   nil                              ;; "TIME OBSERVATIONS STARTED"
   nil                              ;; "TRIP COMMENTS"
   nil                              ;; "SPECIES COMMENTS"
   nil                              ;; "OBSERVER ID"
   nil                              ;; "FIRST NAME"
   nil                              ;; "LAST NAME"
   nil                              ;; "SAMPLING EVENT IDENTIFIER"
   nil                              ;; "PROTOCOL TYPE"
   nil                              ;; "PROJECT CODE"
   nil                              ;; "DURATION MINUTES"
   nil                              ;; "EFFORT DISTANCE KM"
   nil                              ;; "EFFORT AREA HA"
   nil                              ;; "NUMBER OBSERVERS"
   nil                              ;; "ALL SPECIES REPORTED"
   nil                              ;; "GROUP IDENTIFIER"
   nil                              ;; "APPROVED"
   nil                              ;; "REVIEWED"
   nil                              ;; "REASON"
   ])

(defn sighting
  "given a line of text, split on tabs and return the fields we care about
   (indicated by non-nil presence in fields vector)"
  [plaintext-row]
  (into {}
        (remove nil?
                (map #(if % [% %2])
                     fields
                     (cs/split plaintext-row #"\t")))))

(defn sighting-seq
  "Return a lazy sequence of lines from filename, transformed into sighting maps"
  [filename skip-rows]
  (map sighting (take-while (complement nil?)
                            (drop (if skip-rows skip-rows 1)
                                  (line-seq (io/reader filename))))))

(defn coerce [m f & [key & keys]]
  (if key
    (recur (assoc m key (f (get m key))) f keys)
    m))

(defn parse-count [s]
  (if (= "X" s) 1 (Long/parseLong s)))

(defn coerce-values [sighting]
  (-> sighting
      (coerce bigdec
              :sighting/latitude
              :sighting/longitude)
      (coerce inst/read-instant-date
              :sighting/date)
      (coerce parse-count
              :sighting/count)))

(defn split-taxon
  "return a pair of [taxon sighting] split by their attribute namespace"
  [sighting]
  (let [split (group-by (fn [[k v]] (namespace k))
                        sighting)]
    [(into {} (get split "taxon"))
     (into {} (get split "sighting"))]))

(defn seed-data
  "Lazy sequence of lines from file, manipulated into [taxon sighting] pairs"
  [file skip-rows]
  (->> (sighting-seq file skip-rows)
       (map coerce-values)
       (map split-taxon)))
