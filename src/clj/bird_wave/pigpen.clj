(ns bird-wave.pigpen
  (:require [pigpen.core :as pig]
            [pigpen.fold :as fold]
            [clojure.string :as cs]
            [clojure.instant :as inst]))

(defn birds-file
  [filename]
  (pig/load-tsv filename))

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

(def fields (concat (remove nil? field-positions)
                    [:sighting/month-yr]))

(defn sighting
  "given a line of text, split on tabs and return the fields we care about
   (indicated by non-nil presence in fields vector)"
  [row]
  (->> row
       (map #(if % [% %2]) field-positions)
       (remove nil?)
       (into {})))

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

(defn birds-by-us-county
  [data]
  (->> data
       (pig/map
        (fn [row]
          (-> row
              (sighting)
              (coerce-values))))
       (pig/group-by (juxt :taxon/order :sighting/month-yr :sighting/state :sighting/county)
                     {:fold (fold/juxt (fold/count)
                                       (->> (fold/map :sighting/count) (fold/sum))
                                       (->> (fold/map :sighting/count) (fold/avg)))})
       (pig/map flatten)))

(defn species
  [data]
  (->> data
       (pig/map
        (fn [row]
          (-> row
              (sighting)
              (coerce-values))))
       (pig/group-by (juxt :taxon/order :taxon/common-name)
                     {:fold (fold/count)})
       (pig/map flatten)
       ;; (pig/filter (fn [x] (< 1 (last x))))
       ))

(defn transform-birds
  [input-file output-file]
  (->> (birds-file input-file)
       (birds-by-us-county)
       (pig/store-clj output-file)))

(comment

  (pig/dump (->> "sample_data/birds.txt"
                 (birds-file)
                 (birds-by-us-county)))

  (pig/dump (->> "sample_data/birds.txt"
                 (birds-file)
                 (species)))

  )
