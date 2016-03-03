(ns bird-wave.birdlog
  (:require [cascalog.api :refer :all]
            [cascalog.more-taps :as taps]
            [clojure.string :as cs]
            [clojure.instant :as inst]))

(def field-positions
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

(def fields (remove nil? fields))

(defn sighting
  "given a line of text, split on tabs and return the fields we care about
   (indicated by non-nil presence in fields vector)"
  [plaintext-row]
  (into {}
        (remove nil?
                (map #(if % [% %2])
                     field-positions
                     (cs/split plaintext-row #"\t")))))

(defn coerce [m f & [key & keys]]
  (if key
    (recur (assoc m key (f (get m key))) f keys)
    m))

(defn parse-count [s]
  (if (= "X" s) 1 (Long/parseLong s)))

(defn coerce-values [sighting]
  (let [date (inst/read-instant-date (:sighting/date sighting)) ]
    (-> sighting
        (coerce bigdec
                :sighting/latitude
                :sighting/longitude)
        (coerce parse-count
                :sighting/count)
        (assoc :sighting/date date)
        (assoc :sighting/month-yr (format "%tY/%tm" date date))
        )))

(defn ordered-values
  "return values of fields in column order"
  [sighting]
  (map sighting fields))

(defmapop parse-line [line]
  (ordered-values (coerce-values (sighting line))))

(?- (stdout)
    (<- [?common-name ?count]
        ((hfs-textline "sample_data/birds.txt") ?line)
        (parse-line ?line :> ?guid ?order ?common-name ?scientific-name ?subspecies-common-name ?subspecies-scientific-name ?count ?state ?state-code ?county ?county-code ?locality ?latitude ?longitude ?date)))
