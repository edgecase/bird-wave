(ns bird-man.client
  (:require [clojure.browser.repl :as repl]
            [clojure.string :as cs]
            [clojure.walk :refer (keywordize-keys)]
            [goog.string.format :as gformat]
            [bird-man.util :refer (debounce)]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [secretary.core :as secretary :include-macros true :refer [defroute]]
            [goog.events :as events]
            [ankha.core :as ankha])
  (:import goog.History
           goog.history.EventType))

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

(def projection ( -> js/d3 (.geo.albersUsa)))
(def path ( -> js/d3 (.geo.path projection)))

(defonce freq-by-county (atom {}))
(defn target [] (.-target (.-event js/d3)))

;;;;;;;;

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

(defn fetch-month-data []
  (.log js/console "fetch-month-data")
  (let [model @model]
    (when (and (:current-taxon model)
               (:month-yr model)
               (not (has-sightings-for-current-state? model)))
      (.log js/console "doing the fetch for realz")
      (js/d3.json (str "species/" (:current-taxon model) "/" (:month-yr model)) update-counties))))


(defn watch-model
  "When the model changes update the map"
  [watch-name ref old new]
  (.log js/console "month-yr changed: " (changed? :month-yr old new))
  (.log js/console "current-taxon changed: " (changed? :current-taxon old new))
  (if (or (changed? :current-taxon old new)
          (changed? :month-yr old new))
    (fetch-month-data)))

(add-watch model ::model-watch watch-model)

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


(secretary/set-config! :prefix "#")
(events/listen history EventType.NAVIGATE
  (fn [e] (secretary/dispatch! (.-token e))))

(.setEnabled history true)

(defn species-item [taxon owner]
  (reify
    om/IRenderState
    (render-state [this {:keys [current-taxon]}]
      (let [this-taxon (:taxon/order taxon)
            classes (cs/join " " ["taxon" (if (= current-taxon this-taxon) "selected")])]
        (dom/li #js {:className classes}
          (dom/a #js {:href (taxon-path {:order this-taxon})}
            (if-let [sub-name (not-empty (:taxon/subspecies-common-name taxon))]
              sub-name
              (:taxon/common-name taxon))))))))

(defn species-list [model owner]
  (reify
    om/IRender
    (render [this]
      (apply dom/ul
       (om/build-all species-item (:taxonomy model)
                     {:state (select-keys model [:current-taxon])})))))


;;;;;;;;


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

(defn select-bird [data]
  (reset! current-taxon (aget data "taxon/order"))
  ( -> js/d3
       (.selectAll "#species ul li")
       (.classed "selected" false))
  ( -> js/d3
       (.select (target))
       (.classed "selected" true)))

(defn list-birds [species]
  (swap! model assoc :taxonomy (map keywordize-keys (js->clj species))))

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
                                   :month (goog.string.format "%02d" (-> (.getMonth date) (inc) (.toString)))}))))

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
                  (.on "slide" (debounce update-month 500 false))))))

(defn draw-map []
  (js/d3.json "data/us.json" plot))


(defn ^:export start-client []
  (get-birds)
  (draw-map)
  (om/root species-list model {:target (.getElementById js/document "species")})
  (repl/connect "http://localhost:9000/repl"))



;; for debugging
;; (om/root
;;  ankha/inspector
;;  model
;;  {:target (js/document.getElementById "inspector")})
