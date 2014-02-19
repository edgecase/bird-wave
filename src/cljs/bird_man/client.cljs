(ns bird-man.client
  (:require [clojure.browser.repl :as repl]
            [clojure.string :as cs]
            [goog.string.format :as gformat]
            [bird-man.util :refer [debounce]]))

(def svg-dim {:width 900 :height 600})
(def key-dim {:width 10 :height 200})

(def svg (-> js/d3
             (.select "body")
             (.append "div")
             (.attr "id" "map")
             (.append "svg")
             (.attr "height" (:height svg-dim))
             (.attr "width" (:width svg-dim))))

(def slider ( -> js/d3
                 (.select "body")
                 (.append "div")
                 (.attr "id" "slider")))

(def species-list ( -> js/d3
                       (.select "body")
                       (.append "div")
                       (.attr "id" "species")
                       (.append "ul")))

(def projection ( -> js/d3 (.geo.albersUsa)))
(def path ( -> js/d3 (.geo.path projection)))

(defonce freq-by-county (atom {}))
(defonce current-taxon (atom nil))
(defonce current-month-yr (atom nil))
(defn target [] (.-target (.-event js/d3)))

(defn build-key [state county]
  (apply str (interpose "-" [state county])))

(defn populate-freqs [stats]
  (reset! freq-by-county
          (into {}
                (map (fn [s]
                       [(build-key (aget s "state") (aget s "county"))
                        (/ (aget s "total") (aget s "sightings"))])
                     stats))))

(defn freq-for-county [data]
  (let [p (aget data "properties")
       st (str "US-" (aget p "state"))
       cty (first (cs/split (aget p "county") " "))
       keystr (build-key st cty)
       freq (@freq-by-county keystr)]
  (if freq freq 0.0)))

(defn freq-duration [data]
  (+ (* 250 (freq-for-county data)) 200))

(def color ( -> js/d3.scale
                (.quantile)
                (.domain (array 0 5))
                (.range (-> (aget js/colorbrewer.YlGnBu "9") (.reverse)))))

(def months ( -> (js/d3.time.scale)
                 (.domain (array (new js/Date 2012 11) (new js/Date 2013 11)))))

(def key-scale ( -> js/d3.scale
                    (.linear)
                    (.domain (array 5 0))
                    (.range (array 0 (:height key-dim)))))

(def key-axis ( -> (js/d3.svg.axis)
                   (.scale key-scale)
                   (.orient "left")
                   (.tickValues (color.quantiles))
                   (.tickFormat (js/d3.format ".1f"))))

(defn freq-color [data]
  (color (freq-for-county data)))

(defn get-birds []
  (js/d3.json "species" list-birds))

(defn list-birds [species]
  ( -> species-list
       (.selectAll "li")
       (.data species)
       (.enter)
       (.append "li")
       (.text #(aget % "taxon/common-name"))
       (.on "click" select-bird)))

(defn select-bird [data]
  (reset! current-taxon (aget data "taxon/order"))
  ( -> js/d3
       (.selectAll "#species ul li")
       (.classed "selected" false))
  ( -> js/d3
       (.select (target))
       (.classed "selected" true)))

(defn draw-map []
  (js/d3.json "data/us.json" plot))

(defn plot [us]
  (-> svg
      (.append "g")
      (.selectAll "path")
      (.data (aget (js/topojson.feature us (aget us "objects" "counties") #(not (= %1 %2))) "features"))
      (.enter)
      (.append "path")
      (.classed "county" true)
      (.attr "d" path))
  ( -> svg
       (.append "path")
       (.datum (js/topojson.mesh us (aget us "objects" "states")))
       (.classed "states" true)
       (.attr "d" path))
  (def key-g ( -> svg
                  (.append "g")
                  (.classed "axis" true)
                  (.attr "transform", (str "translate(" (- (:width svg-dim) (:width key-dim)) "," (/ (:height svg-dim) 1.5) ")"))))
  (-> key-g
      (.selectAll "rect")
      (.data (-> (.range color) (.map #(.invertExtent color %))))
      (.enter)
      (.append "rect")
      (.attr "height" #(- (:height key-dim) (key-scale (- (nth % 1) (nth % 0)))))
      (.attr "width" 8)
      (.attr "y" #(key-scale (nth % 1)))
      (.style "fill" #(nth (.range color) %2)))
  (-> key-g (.call key-axis))
  ( -> slider
       (.call (-> (js/d3.slider)
                  (.axis true)
                  (.scale months)
                  (.tickFormat (js/d3.time.format "%B"))
                  (.step (* 1000 60 60 24))
                  (.on "slide" (debounce fetch-month-data 500 false))))))

(defn update-counties [results]
  (populate-freqs results)
  ( -> js/d3
       (.selectAll "path.county")
       (.transition)
       (.duration freq-duration)
       (.style "fill" freq-color)))

(defn fetch-month-data [slide timestamp]
  (let [date (new js/Date timestamp)
        month-yr (str "/" (.getFullYear date) "/" (goog.string.format "%02d" (-> (.getMonth date) (inc) (.toString))))]
    (js/console.log month-yr)
    (when-not (= month-yr @current-month-yr)
      (reset! current-month-yr month-yr)
      (when-not (nil? @current-taxon) (js/d3.json (str "species/" @current-taxon @current-month-yr) update-counties)))))

(defn ^:export start-client []
  (get-birds)
  (draw-map)
  (repl/connect "http://localhost:9000/repl"))

