(ns bird-man.import
  (:require [clojure.string :as cs]
            [clojure.java.io :as io]
            [clojure.instant :as inst]))

(def fields
  [:sighting/guid                   ;; "GLOBAL UNIQUE IDENTIFIER"
   nil                              ;; "TAXONOMIC ORDER"
   nil                              ;; "CATEGORY"
   :sighting/common-name            ;; "COMMON NAME"
   nil                              ;; "SCIENTIFIC NAME"
   :sighting/subspecies-common-name ;; "SUBSPECIES COMMON NAME"
   nil                              ;; "SUBSPECIES SCIENTIFIC NAME"
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
   :sighting/event-id               ;; "SAMPLING EVENT IDENTIFIER"
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

(defn sighting [plaintext-row]
  (into {}
        (filter (complement nil?)
                (map #(if % [% %2])
                     fields
                     (cs/split plaintext-row #"\t")))))

(defn sighting-seq [filename]
  (map sighting (drop 1 (line-seq (io/reader filename)))))

(comment
  (take 1 (sighting-seq "/Users/bestra/Downloads/ebird.txt")))

(defn coerce [m f & [key & keys]]
  (if key
    (recur (assoc m key (f (get m key))) f keys)
    m))

(defn sighting-seed [sighting]
  (-> sighting
      (coerce #(bigdec %)
              :sighting/latitude
              :sighting/longitude)
      (coerce #(inst/read-instant-date %)
              :sighting/date)
      (coerce #(Long/parseLong %)
              :sighting/count)))
