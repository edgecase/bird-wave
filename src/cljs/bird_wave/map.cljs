(ns bird-wave.map
  (:require [clojure.string :as cs]
            [bird-wave.util :refer (analytic-event)]))

(def svg-dim {:width 768 :height 500})
(def key-dim {:width 10 :height 200})
(def key-bg-dim {:width 45 :height 210})
(def max-freq 5)
(def zoom-duration 550)
(def projection ( -> js/d3 (.geo.albersUsa) (.scale 900) (.translate (array (+ 10 (/ (:width svg-dim) 2)) (/ (:height svg-dim) 2)))))
(def path ( -> js/d3 (.geo.path) (.projection projection)))
(def color ( -> js/d3.scale
                (.quantile)
                (.domain (array 0 max-freq))
                (.range (-> (aget js/colorbrewer.YlGnBu "9") (.reverse)))))
(def months ( -> (js/d3.time.scale)
                 (.domain (array (new js/Date 2012 10 15) (new js/Date 2013 10 15)))
                 (.range (array 0 (:width svg-dim)))))
(def key-scale ( -> js/d3.scale
                    (.linear)
                    (.domain (array max-freq 0))
                    (.range (array 0 (:height key-dim)))))
(def key-axis ( -> (js/d3.svg.axis)
                   (.scale key-scale)
                   (.orient "left")
                   (.tickValues (color.quantiles))
                   (.tickFormat (js/d3.format ".1f"))))
(defn handle-zoom []
  (-> js/d3
      (.selectAll "g.topo")
      (.style "stroke-width" (str (/ 1.2 (aget js/d3 "event" "scale")) "px"))
      (.attr "transform" (str "translate(" (aget js/d3 "event" "translate") ") scale(" (aget js/d3 "event" "scale") ")"))))
(def zoom ( -> js/d3.behavior
               (.zoom)
               (.translate (array 0 0))
               (.scale 1)
               (.scaleExtent (array 1 8))
               (.on "zoom" handle-zoom)))

(defn target [] (.-target (.-event js/d3)))
(defn state-to-activate [] (.select js/d3 (target)))
(defn active-state [] (.select js/d3 ".active"))
(defn active-attrs [el]
  (let [bounds (.bounds path el)
        width (:width svg-dim)
        height (:height svg-dim)
        dx (- (aget bounds 1 0) (aget bounds 0 0))
        dy (- (aget bounds 1 1) (aget bounds 0 1))
        x  (/ (+ (aget bounds 0 0) (aget bounds 1 0)) 2)
        y  (/ (+ (aget bounds 0 1) (aget bounds 1 1)) 2)
        scale (/ 0.9 (js/Math.max (/ dx width) (/ dy height)))
        translate (array (- (/ width 2) (* scale x)) (- (/ height 2) (* scale y)))]
    {:scale scale :translate translate}))

(defn state-name [el]
  (aget el "properties" "state"))

(defn init-axis [selector]
  (-> js/d3
      (.select selector)
      (.call (-> (js/d3.svg.axis)
                 (.scale months)
                 (.tickFormat (js/d3.time.format "%B"))))))

(defn reset [svg]
  (.classed (active-state) "active" false)
  (-> svg
      (.transition)
      (.duration zoom-duration)
      (.call (.-event (-> zoom
                          (.translate (array 0 0))
                          (.scale 1))))))

(defn zoom-state [svg state]
  (let [zoom-attrs (active-attrs state)]
    (if (= (.node (state-to-activate)) (.node (active-state)))
      (reset svg)
      (do
        (.classed (active-state) "active" false)
        (.classed (state-to-activate) "active" true)
        (-> svg
            (.transition)
            (.duration zoom-duration)
            (.call (.-event (-> zoom
                                (.translate (:translate zoom-attrs))
                                (.scale (:scale zoom-attrs))))))
        (analytic-event {:category "zoom" :action "click-state" :label (state-name state)})))))

(defn prevent-zoom-on-drag []
  (let [e (.-event js/d3)]
    (when (.-defaultPrevented e) (.stopPropagation e))))

(defn plot [svg us]
  (let [s-width (:width svg-dim)
        s-height (:height svg-dim)
        k-width (:width key-dim)
        k-height (:height key-dim)
        b-width (:width key-bg-dim)
        b-height (:height key-bg-dim)
        counties (aget us "objects" "counties")
        states (aget us "objects" "states")]
    (-> svg
        (.selectAll "g.topo")
        (.remove))
    (-> svg
        (.append "rect")
        (.classed "background" true)
        (.attr "width" s-width)
        (.attr "height" s-height)
        (.on "click" #(reset svg)))
    (if counties
      (-> svg
          (.append "g")
          (.classed "topo" true)
          (.selectAll "path")
          (.data (aget (js/topojson.feature us counties) "features"))
          (.enter)
          (.append "path")
          (.classed "county" true)
          (.attr "d" path)))
    (-> svg
        (.append "g")
        (.classed "topo" true)
        (.selectAll "path")
        (.data (aget (js/topojson.feature us states) "features"))
        (.enter)
        (.append "path")
        (.classed "state" true)
        (.attr "d" path)
        (.on "click" #(zoom-state svg %)))
    (-> svg
        (.call zoom)
        (.call (.-event zoom)))
    (-> svg
        (.append "rect")
        (.classed "axis-background" true)
        (.attr "width" b-width)
        (.attr "height" b-height)
        (.attr "rx" 4)
        (.attr "ry" 4)
        (.attr "transform", (str "translate(" (- s-width b-width 5) "," (- s-height b-height) ")")))
    (-> svg
        (.append "text")
        (.classed "legend" true)
        (.text "Scale represents the ratio of birds sighted to number of sightings")
        (.attr "transform", (str "translate(" (- s-width 390) "," (- s-height 20) ")")))
    (-> svg
        (.append "text")
        (.classed "legend" true)
        (.text "eBird Basic Dataset. Version: EBD_relNov-2013. Cornell Lab of Ornithology, Ithaca, New York. November 2013.")
        (.attr "transform", (str "translate(" (- s-width 627) "," (- s-height 5) ")")))
    (-> svg
        (.append "g")
        (.classed "axis" true)
        (.call key-axis)
        (.attr "transform", (str "translate(" (- s-width k-width 10) "," (- s-height k-height 5) ")"))
        (.selectAll "rect")
        (.data (-> (.range color) (.map #(.invertExtent color %))))
        (.enter)
        (.append "rect")
        (.attr "height" #(- k-height (key-scale (- (nth % 1) (nth % 0)))))
        (.attr "width" 8)
        (.attr "y" #(key-scale (nth % 1)))
        (.style "fill" #(nth (.range color) %2)))))

(defn draw-map [svg file]
  (js/d3.json (str "data/" file)
              (fn [us]
                (aset js/window "mapdata" us)
                (plot svg us))))

(defn init-map [svg-sel model]
  (let [svg (-> js/d3
                (.select svg-sel)
                (.on "click" prevent-zoom-on-drag true))
        data-file (if (= (:screen-size model) "lg") "us.json" "us-states.json")]
    (draw-map svg data-file)))

(defn build-key [state county]
  (apply str (interpose "-" [state county])))

(defn make-state-frequencies [stats]
  (into {}
        (map (fn [s]
               [(aget s "state")
                (/ (aget s "total") (aget s "sightings"))])
             stats)))

(defn make-county-frequencies [stats]
  (into {}
        (map (fn [s]
               [(build-key (aget s "state") (aget s "county"))
                (/ (aget s "total") (aget s "sightings"))])
             stats)))

(defn make-frequencies [method stats]
  (case method
    "state" (make-state-frequencies stats)
    "county" (make-county-frequencies stats)))

(defn freq-for-county [frequencies data]
  (let [p (aget data "properties")
        st (str "US-" (aget p "state"))
        cty (first (cs/split (aget p "county") " "))
        keystr (build-key st cty)
        freq (get frequencies keystr)]
  (if freq freq 0.0)))

(defn freq-for-state [frequencies data]
  (let [p (aget data "properties")
        st (str "US-" (aget p "state"))
        freq (get frequencies st)]
  (if freq freq 0.0)))

(defn freq-duration-county [frequencies]
  (fn [data]
    (+ (* 100 (freq-for-county frequencies data)) 200)))

(defn freq-color-county [frequencies]
  (fn [data]
    (color (freq-for-county frequencies data))))

(defn freq-duration-state [frequencies]
  (fn [data]
    (+ (* 100 (freq-for-state frequencies data)) 200)))

(defn freq-color-state [frequencies]
  (fn [data]
    (color (freq-for-state frequencies data))))

(defn update-counties [model]
  ( -> js/d3
       (.selectAll "path.county")
       (.transition)
       (.duration (freq-duration-county model))
       (.style "fill" (freq-color-county model))
       (.style "stroke" (freq-color-county model))))

(defn update-states [model]
  ( -> js/d3
       (.selectAll "path.state")
       (.transition)
       (.duration (freq-duration-state model))
       (.style "fill" (freq-color-state model))))

(defn update-map [method model]
  (case method
    "state" (update-states model)
    "county" (update-counties model)))
