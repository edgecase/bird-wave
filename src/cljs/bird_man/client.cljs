(ns bird-man.client
  (:require [clojure.browser.repl :as repl]
            [clojure.string :as cs]
            [goog.string.format :as gformat]
            [bird-man.util :refer [debounce]]))

(def svg-dim {:width 1000 :height 600})
(def slider-width 800)

(def svg (-> js/d3
             (.select "body")
             (.append "div")
             (.attr "id" "#map")
             (.append "svg")
             (.attr "height" (:height svg-dim))
             (.attr "width" (:width svg-dim))))

(def slider ( -> js/d3
                 (.select "body")
                 (.append "div")
                 (.attr "id" "#slider")))

(def path ( -> js/d3 (.geo.path)))

(defonce freq-by-county (atom {}))
(defonce current-taxon (atom nil))
(defonce current-month-yr (atom nil))

(defn build-key [state county]
  (apply str (interpose "-" [state county])))

(defn populate-freqs [stats]
  (reset! freq-by-county {})
  (doseq [s stats]
    (swap! freq-by-county assoc (build-key (aget s "state") (aget s "county")) (/ (aget s "total") (aget s "sightings")))))

(defn freq-for-county [data]
  (let [p (aget data "properties")
       st (str "US-" (aget p "state"))
       cty (first (cs/split (aget p "county") " "))
       keystr (build-key st cty)
       freq (@freq-by-county keystr)]
  (if freq freq 0.0)))

(defn freq-duration [data]
  (* 500 (freq-for-county data)))

(def color ( -> js/d3.scale
                (.quantile)
                (.domain (array 0 5))
                (.range (-> (aget js/colorbrewer.YlGnBu "9") (.reverse)))))

(def months ( -> (js/d3.time.scale)
                 (.domain (array (new js/Date 2012 11) (new js/Date 2013 11)))))

(def month-axis ( -> (js.d3.svg.axis)
                     (.scale months)
                     (.orient "bottom")
                     (.ticks js/d3.time.months)
                     (.tickSize 16 0)
                     (.tickFormat (js/d3.time.format "%B"))
                     ))

(defn freq-color [data]
  (color (freq-for-county data)))

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
  ( -> slider
       (.call (-> (js/d3.slider)
                  (.axis true)
                  (.scale months)
                  (.tickFormat (js/d3.time.format "%B"))
                  (.step (* 1000 60 60 24))
                  (.on "slide" (debounce fetch-month-data 500 false))
                  ))))

(defn draw-map []
  (js/d3.json "data/us.json" plot))

(defn ^:export start-client []
  (-> js.d3
      (.select "select.bird")
      (.on "change" #(reset! current-taxon (.-value (.-target (.-event js/d3))))))
  (draw-map)
  (repl/connect "http://localhost:9000/repl"))

