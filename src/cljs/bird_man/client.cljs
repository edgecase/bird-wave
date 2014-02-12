(ns bird-man.client
  (:require [clojure.browser.repl :as repl]
            [clojure.string :as cs]))

(def svg-dim {:width 950 :height 500})

(def svg (-> js/d3
             (.select "body")
             (.append "svg")
             (.attr "height" (:height svg-dim))
             (.attr "width" (:width svg-dim))))

(def path ( -> js/d3 (.geo.path)))

(def color ( -> js/d3.scale
                (.threshold)
                (.domain (js/Array 0.5 1.0 1.5 2.0 2.5))
                (.range (js/Array "#f2f0f7" "#dadedb" "#bcbddc" "#9e9ac8" "#756bb1" "#54278f"))))

(def freq-by-county (atom {}))

(defn build-key [state county]
  (apply str (interpose "-" [state county])))

(defn populate-freqs [stats]
  (doseq [s stats]
    (swap! freq-by-county assoc (build-key (aget s "state") (aget s "county")) (/ (aget s "total") (aget s "sightings")))))

(defn freq-color [data]
  (let [p (aget data "properties")
        st (str "US-" (aget p "state"))
        cty (first (cs/split (aget p "county") " "))
        keystr (build-key st cty)]
    (color (@freq-by-county keystr))))

(defn draw [err us results]
  (populate-freqs results)
  ( -> svg
       (.append "g")
       (.selectAll "path")
       (.data (aget (js/topojson.feature us (aget us "objects" "counties") #(not (= %1 %2))) "features"))
       (.enter)
       (.append "path")
       (.style "fill" freq-color)
       (.classed "county" true)
       (.attr "d" path))
  ( -> svg
       (.append "path")
       (.datum (js/topojson.mesh us (aget us "objects" "states")))
       (.classed "states" true)
       (.attr "d" path)))

(defn draw-map []
  ( -> (js/queue)
     (.defer js/d3.json "data/us.json")
     (.defer js/d3.json "species/Tufted Titmouse")
     (.await draw)))

(defn ^:export start-client []
  (draw-map)
  (repl/connect "http://localhost:9000/repl"))

