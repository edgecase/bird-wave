(ns bird-man.client
  (:require [clojure.browser.repl :as repl]
            [clojure.string :as cs]
            [clojure.walk :refer (keywordize-keys)]
            [goog.string.format :as gformat]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [secretary.core :as secretary :include-macros true :refer [defroute]]
            [goog.events :as events]
            [ankha.core :as ankha])
  (:import goog.History
           goog.history.EventType))

(def svg-dim {:width 900 :height 560})
(def max-freq 5)
(def key-dim {:width 10 :height 200})
(def projection ( -> js/d3 (.geo.albersUsa) (.scale 1000) (.translate (array (/ (:width svg-dim) 2) (/ (:height svg-dim) 2)))))
(def path ( -> js/d3 (.geo.path) (.projection projection)))
(def color ( -> js/d3.scale
                (.quantile)
                (.domain (array 0 max-freq))
                (.range (-> (aget js/colorbrewer.YlGnBu "9") (.reverse)))))
(def months ( -> (js/d3.time.scale)
                 (.domain (array (new js/Date 2012 10 15) (new js/Date 2013 10 15)))
                 (.range (array 0 (:width svg-dim)))))
(def month-axis (-> (js/d3.svg.axis)
                    (.scale months)
                    (.tickFormat (js/d3.time.format "%B"))))
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
      (.style "stroke-width" (str (/ 1.3 (aget js/d3 "event" "scale")) "px"))
      (.attr "transform" (str "translate(" (aget js/d3 "event" "translate") ") scale(" (aget js/d3 "event" "scale") ")"))))
(def zoom ( -> js/d3.behavior
               (.zoom)
               (.translate (array 0 0))
               (.scale 1)
               (.scaleExtent (array 1 8))
               (.on "zoom" handle-zoom)))

(defonce freq-by-county (atom {}))
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

(def model (atom {:current-taxon nil
                  :month-yr nil     ; selected month
                  :taxonomy []      ; all taxons
                  :sightings {}     ; sightings for selected taxon, grouped by month-yr
                  }))

(defn changed? [key old new]
  (.log js/console (name key) (key old) (key new))
  (not= (get old key) (get new key)))

(defn has-sightings-for-current-state? [model]
  false)

(declare update-counties)

(defn fetch-month-data [model]
  (.log js/console "fetch-month-data")
  (when (and (:current-taxon model)
             (:month-yr model)
             (not (has-sightings-for-current-state? model)))
    (.log js/console "doing the fetch for realz")
    (js/d3.json (str "species/" (:current-taxon model) "/" (:month-yr model)) update-counties)))

(defn watch-model
  "When the model changes update the map"
  [watch-name ref old new]
  (.log js/console "month-yr changed: " (changed? :month-yr old new))
  (.log js/console "current-taxon changed: " (changed? :current-taxon old new))
  (if (or (changed? :current-taxon old new)
          (changed? :month-yr old new))
    (fetch-month-data new)))


(def history (History.))

(defn push-state [token]
  (.setToken history (cs/replace token #"^#" ""))
  (secretary/dispatch! token))

(defroute "/" [] (swap! model assoc :current-taxon :nil :month-yr nil))

(defroute taxon-month-path "/taxon/:order/:year/:month" [order year month]
  (.log js/console "taxon-month-path" order year month)
  ;; TODO: validate year and month
  (swap! model assoc :current-taxon order :month-yr (str year "/" month)))

(defroute taxon-path "/taxon/:order" [order]
  (.log js/console "taxon-path")
  (push-state (taxon-month-path {:order order, :year 2012, :month 12})))


(defn update-location
  "Use this function when one of the controls (species or time) changes.
Will only affect history if there is a species selected."
  [changes]
  (let [model @model
        month-yr (get changes :month-yr (or (:month-yr model) "2012/12"))
        taxon (get changes :current-taxon (:current-taxon model))
        [_ year month] (re-find #"(\d{4})/(\d{2})" month-yr)]
    (if taxon
      (push-state (taxon-month-path {:order taxon, :month month :year year})))))

(defn species-item [taxon owner]
  (reify
    om/IRenderState
    (render-state [this {:keys [current-taxon]}]
      (let [this-taxon (:taxon/order taxon)
            classes (cs/join " " ["taxon" (if (= current-taxon this-taxon) "selected")])]
        (dom/li #js {:className classes}
          (dom/a #js {:href (taxon-path {:order this-taxon})
                      :onClick (fn [e]
                                 (update-location {:current-taxon this-taxon})
                                 false)}
            (if-let [sub-name (not-empty (:taxon/subspecies-common-name taxon))]
              sub-name
              (:taxon/common-name taxon))))))))

(defn species-list [model owner]
  (reify
    om/IRender
    (render [this]
      (apply dom/ul #js {}
       (om/build-all species-item (:taxonomy model)
                     {:state (select-keys model [:current-taxon])})))))


(def dates #js ["2012/12" "2013/01" "2013/02" "2013/03" "2013/04" "2013/05" "2013/06"
                "2013/07" "2013/08" "2013/09" "2013/10" "2013/11"])

(defn date-slider [model owner]
  (reify
    om/IRender
    (render [this]
      (let [val (if-let [date (:month-yr model)]
                  (.indexOf dates date)
                  0)]
        (dom/input
         #js {:type "range", :min 0, :max 11, :value val
              :onChange #(update-location
                          {:month-yr (get dates (js/parseInt (.. % -target -value)))})})))))


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

(defn freq-color [data]
  (color (freq-for-county data)))

(defn list-birds [species]
  (let [mdl @model
        taxonomy (map keywordize-keys (js->clj species))
        taxon (or (:current-taxon mdl) (:taxon/order (rand-nth taxonomy)))
        month-yr (or (:month-yr mdl) "2012/12")]
    (.log js/console taxon month-yr)
    (swap! model assoc :taxonomy taxonomy)
    (update-location {:current-taxon taxon :month-yr month-yr})))

(defn get-birds []
  (js/d3.json "species" list-birds))

(defn update-counties [results]
  (populate-freqs results)
  ( -> js/d3
       (.selectAll "path.county")
       (.transition)
       (.duration freq-duration)
       (.style "fill" freq-color)))

(defn update-month [slide timestamp]
  (let [date (new js/Date timestamp)]
    (push-state (taxon-month-path {:order (:current-taxon @model)
                                   :year  (.getFullYear date)
                                   :month (goog.string.format "%02d" (-> (.getMonth date) (inc) (.to String)))}))))

(defn reset [svg]
  (.classed (active-state) "active" false)
  (-> svg
      (.transition)
      (.duration 750)
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
            (.duration 750)
            (.call (.-event (-> zoom
                                (.translate (:translate zoom-attrs))
                                (.scale (:scale zoom-attrs))))))))))

(defn prevent-zoom-on-drag []
  (let [e (.-event js/d3)]
    (when (.-defaultPrevented e) (.stopPropagation e))))

(defn plot [svg us]
  (let [s-width (:width svg-dim)
        s-height (:height svg-dim)
        k-width (:width key-dim)
        k-height (:height key-dim)
        key-g (-> svg
                  (.append "g")
                  (.classed "axis" true)
                  (.attr "transform", (str "translate(" (- s-width k-width) "," (/ s-height 1.6) ")")))]
    (-> svg
        (.append "rect")
        (.classed "background" true)
        (.attr "width" s-width)
        (.attr "height" s-height)
        (.on "click" #(reset svg)))
    (-> svg
        (.append "g")
        (.classed "topo" true)
        (.selectAll "path")
        (.data (aget (js/topojson.feature us (aget us "objects" "counties")) "features"))
        (.enter)
        (.append "path")
        (.classed "county" true)
        (.attr "d" path))
    (-> svg
        (.append "g")
        (.classed "topo" true)
        (.selectAll "path")
        (.data (aget (js/topojson.feature us (aget us "objects" "states")) "features"))
        (.enter)
        (.append "path")
        (.classed "state" true)
        (.attr "d" path)
        (.on "click" #(zoom-state svg %)))
    (-> svg
        (.call zoom)
        (.call (.-event zoom)))
    (-> key-g
        (.selectAll "rect")
        (.data (-> (.range color) (.map #(.invertExtent color %))))
        (.enter)
        (.append "rect")
        (.attr "height" #(- k-height (key-scale (- (nth % 1) (nth % 0)))))
        (.attr "width" 8)
        (.attr "y" #(key-scale (nth % 1)))
        (.style "fill" #(nth (.range color) %2)))
    (-> key-g (.call key-axis))))

(defn draw-map [svg]
  (js/d3.json "data/us.json"
              #(plot svg %)))


(defn ^:export start []
  (let [svg (-> js/d3
                (.select "#map")
                (.append "svg")
                (.attr "height" (:height svg-dim))
                (.attr "width" (:width svg-dim))
                (.on "click" prevent-zoom-on-drag true))
        slider (-> js/d3
                   (.select "#slider")
                   (.append "svg")
                   (.append "g")
                   (.classed "axis" true)
                   (.call month-axis))]

    (add-watch model ::model-watch watch-model)
    (secretary/set-config! :prefix "#")
    (events/listen history EventType.NAVIGATE
                   (fn [e] (secretary/dispatch! (.-token e))))
    (.setEnabled history true)

    (draw-map svg)
    (get-birds)
    (om/root species-list model {:target (.getElementById js/document "species")})
    (om/root date-slider model {:target (.getElementById js/document "date-input")})
    (repl/connect "http://localhost:9000/repl")))



;; for debugging
;; (om/root
;;  ankha/inspector
;;  model
;;  {:target (js/document.getElementById "inspector")})
