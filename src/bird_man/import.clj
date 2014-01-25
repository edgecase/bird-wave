(ns bird-man.import
 (:require [clojure.string :as cs]
           [clojure.java.io :as io]))

(def fields
  [
   :sighting/guid true ;;  "GLOBAL UNIQUE IDENTIFIER"
   :a01 false;;  "TAXONOMIC ORDER"
   :a02 false;;  "CATEGORY"
   :sighting/common-name true ;;  "COMMON NAME"
   :a03 false;;  "SCIENTIFIC NAME"
   :sighting/subspecies-common-name true ;;  "SUBSPECIES COMMON NAME"
   :a04 false;;  "SUBSPECIES SCIENTIFIC NAME"
   :sighting/count true ;;  "OBSERVATION COUNT" ;; x indicates uncounted
   :a05 false;;  "BREEDING BIRD ATLAS CODE"
   :a06 false;;  "AGE/SEX"
   :a07 false;;  "COUNTRY"
   :a08 false;;  "COUNTRY_CODE"
   :sighting/state true ;;  "STATE_PROVINCE"
   :sighting/state-code true ;;  "SUBNATIONAL1_CODE"
   :sighting/county true ;;  "COUNTY"
   :sighting/county-code true ;;  "SUBNATIONAL2_CODE"
   :a09 false;;  "IBA CODE"
   :sighting/locality true ;;  "LOCALITY"
   :a10 false;;  "LOCALITY ID"
   :a11 false;;  "LOCALITY TYPE"
   :sighting/latitude true ;;  "LATITUDE"
   :sighting/longitude true ;;  "LONGITUDE"
   :sighting/date true ;;  "OBSERVATION DATE"
   :a12 false;;  "TIME OBSERVATIONS STARTED"
   :a13 false;;  "TRIP COMMENTS"
   :a14 false;;  "SPECIES COMMENTS"
   :a15 false;;  "OBSERVER ID"
   :a16 false;;  "FIRST NAME"
   :a17 false;;  "LAST NAME"
   :sighting/event-id true ;;  "SAMPLING EVENT IDENTIFIER"
   :a18 false;;  "PROTOCOL TYPE"
   :a19 false;;  "PROJECT CODE"
   :a20 false;;  "DURATION MINUTES"
   :a21 false;;  "EFFORT DISTANCE KM"
   :a22 false;;  "EFFORT AREA HA"
   :a23 false;;  "NUMBER OBSERVERS"
   :a24 false;;  "ALL SPECIES REPORTED"
   :a25 false;;  "GROUP IDENTIFIER"
   :a26 false;;  "APPROVED"
   :a27 false;;  "REVIEWED"
   :a28 false;;  "REASON"
   ])

(defn sighting-fields [[key include?] val]
  (if include?
    [key val] nil)
)

(defn sighting [plaintext-row]
  (into {}
        (filter (complement nil?)
                (map sighting-fields
                     (partition 2 fields)
                     (cs/split plaintext-row #"\t")))))

(defn sighting-seq [filename]
  (map sighting (drop 1 (line-seq (io/reader filename)))))

(take 1 (sighting-seq "/Users/bestra/Downloads/ebird.txt"))
