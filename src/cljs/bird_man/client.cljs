(ns bird-man.client
  (:require [clojure.string :as cs]
            [clojure.walk :refer (keywordize-keys)]
            [goog.string.format :as gformat]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [goog.events :as events]
            [ankha.core :as ankha])
  (:import goog.History
           goog.history.EventType))

(defn log [& args]
  (if js/window.__birdman_debug__
    (.log js/console (pr-str args))))

(def svg-dim {:width 800 :height 500})
(def key-dim {:width 10 :height 200})
(def key-bg-dim {:width 45 :height 210})
(def max-freq 5)
(def zoom-duration 550)
(def projection ( -> js/d3 (.geo.albersUsa) (.scale 940) (.translate (array (+ 10 (/ (:width svg-dim) 2)) (/ (:height svg-dim) 2)))))
(def path ( -> js/d3 (.geo.path) (.projection projection)))
(def color ( -> js/d3.scale
                (.quantile)
                (.domain (array 0 max-freq))
                (.range (-> (aget js/colorbrewer.YlGnBu "9") (.reverse)))))
(def months ( -> (js/d3.time.scale)
                 (.domain (array (new js/Date 2012 10 15) (new js/Date 2013 10 15)))
                 (.range (array 0 (- (:width svg-dim) 10)))))
(def month-axis )
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
                  :filter {:text ""}}))

(defn changed? [key old new]
  (not= (get old key) (get new key)))

(defn has-sightings-for-current-state? [model]
  false)

(declare update-counties)

(defn fetch-month-data [model]
  (when (and (:current-taxon model)
             (:month-yr model)
             (not (has-sightings-for-current-state? model)))
    (js/d3.json (str "species/" (:current-taxon model) "/" (:month-yr model)) update-counties)))

(defn watch-model
  "When the model changes update the map"
  [watch-name ref old new]
  (if (or (changed? :current-taxon old new)
          (changed? :month-yr old new))
    (fetch-month-data new)))


;; (def history (History.))

;; (defn push-state [token]
;;   (.setToken history (cs/replace token #"^#" ""))
;;   )


#_(defn update-location
  "Use this function when one of the controls (species or time) changes.
Will only affect history if there is a species selected."
  [changes]
  (let [model @model
        month-yr (get changes :month-yr (or (:month-yr model) "2012/12"))
        taxon (get changes :current-taxon (:current-taxon model))
        [_ year month] (re-find #"(\d{4})/(\d{2})" month-yr)]
    (if taxon
      (push-state (taxon-month-path {:order taxon, :month month :year year})))))

;; :onClick (fn [e] (update-location {:current-taxon this-taxon}) false)

(defn taxon-path [taxon]
  (str "/#/taxon/" taxon))

;; (cond-> "taxon"
;;         (:filtered? taxon) (str " filtered")
;;         (:current? taxon) (str " current"))

(defn species-item [taxon owner]
  (reify
    om/IRender
    (render [_]
      (let [class-names (if (:hidden? taxon) "taxon hidden" "taxon")]
        (dom/li #js {:className class-names}
                (dom/a #js {:href (taxon-path (:taxon/order taxon))}
                       (first (filter not-empty [(:taxon/subspecies-common-name taxon) (:taxon/common-name taxon)]))))))
    om/IDidMount
    (did-mount [this]
      (let [node (om/get-node owner)
            classes (.-classList node)]
        (when (.contains classes "selected")
          (.scrollIntoView node false))))))

(defn parse-route [url-fragment]
  (let [[_ route taxon-order year month] (cs/split url-fragment "/")]
    {:current-taxon taxon-order, :month-yr (str year "/" month)}))

(defn historian [model owner]
  (reify
    om/IInitState
    (init-state [_]
      (let [history (History.)]
        (events/listen history EventType.NAVIGATE ;;#(secretary/dispatch! (.-token %))
                       (fn [e]
                         (let [route (parse-route (.-token e))]
                           (log "history event" route)
                           (om/update! model :current-taxon (:current-taxon route))
                           (om/update! model :month-yr (:month-yr route)))))
        (.setEnabled history true)

        {:history history}))
    om/IRender
    (render [_] (dom/div nil))))

(defn species-filter [model owner]
  (reify
    om/IRender
    (render [_]
      (log :species-filter :render)
      (dom/input #js {:type "text"
                      :value (:text model)
                      :className "typeahead"
                      :onChange #(om/update! model :text (.. % -target -value))}
                 (dom/i #js {:className "icon-search"})))))

(defn species-list [model owner]
  (reify
    om/IDidMount
    (did-mount [_]
      (log :species-list :mount))
    om/IRender
    (render [_]
      (log :species-list :render)
      (apply dom/ul nil
             (om/build-all species-item model)))))


(def dates #js ["2012/12" "2013/01" "2013/02" "2013/03" "2013/04" "2013/05" "2013/06"
                "2013/07" "2013/08" "2013/09" "2013/10" "2013/11"])

;; :onChange #(update-location {:month-yr (get dates (js/parseInt (.. % -target -value)))})
(defn date-slider [model owner]
  (reify
    om/IRender
    (render [_]
      (log :date-slider)
      (let [val (if-let [date (:month-yr model)]
                  (.indexOf dates date)
                  0)]
        (dom/div #js {:id "slider"}
                 (dom/div #js {:id "date-input"}
                          (dom/input #js {:type "range", :min 0, :max 11, :value val}))
                 (dom/svg nil
                          (dom/g #js {:className "axis"})))))
    om/IDidMount
    (did-mount [_]
      (-> js/d3
          (.select ".axis")
          (.call (-> (js/d3.svg.axis)
                     (.scale months)
                     (.tickFormat (js/d3.time.format "%B"))))))))



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
  (+ (* 100 (freq-for-county data)) 200))

(defn freq-color [data]
  (color (freq-for-county data)))

(defn list-birds [species]
  )

(defn get-birds [model]
  (js/d3.json "species"
              (fn [species]
                (let [taxonomy (map keywordize-keys (js->clj species))
                      taxon (or (:current-taxon @model) (:taxon/order (rand-nth taxonomy)))
                      month-yr (or (:month-yr @model) "2012/12")]
                  (log :get-birds taxon month-yr)
                  (om/update! model :month-yr month-yr)
                  (om/update! model :taxon taxon)
                  (om/update! model :taxonomy (vec taxonomy))
                  ;;(update-location {:current-taxon taxon :month-yr month-yr})
                  ))))

(defn update-counties [results]
  (populate-freqs results)
  ( -> js/d3
       (.selectAll "path.county")
       (.transition)
       (.duration freq-duration)
       (.style "fill" freq-color)
       (.style "stroke" freq-color)))

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
                                (.scale (:scale zoom-attrs))))))))))

(defn prevent-zoom-on-drag []
  (let [e (.-event js/d3)]
    (when (.-defaultPrevented e) (.stopPropagation e))))

(defn plot [svg us]
  (let [s-width (:width svg-dim)
        s-height (:height svg-dim)
        k-width (:width key-dim)
        k-height (:height key-dim)
        b-width (:width key-bg-dim)
        b-height (:height key-bg-dim)]
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
        (.attr "transform", (str "translate(" (- s-width 390) "," (- s-height 5) ")")))
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

(defn draw-map [svg]
  (js/d3.json "data/us.json"
              (fn [us]
                (aset js/window "mapdata" us)
                (plot svg us))))

(defn filter-taxonomy [filter-text root-cursor]
  (let [filter-re (re-pattern (str ".*" (.toLowerCase filter-text) ".*"))]
    (loop [taxonomy (:taxonomy @root-cursor) index 0]
      (when taxonomy
        (let [taxon        (first taxonomy)
              hidden?      (:hidden? taxon)
              species-name (.toLowerCase (str (:taxon/subspecies-common-name taxon) (:taxon/common-name taxon)))
              have-match?  (re-find filter-re species-name)]

          (cond
           (and have-match? hidden?)
           (om/transact! root-cursor [:taxonomy index] #(assoc % :hidden? false))

           (and (not have-match?) (not hidden?))
           (om/transact! root-cursor [:taxonomy index] #(assoc % :hidden? true)))
          (recur (next taxonomy) (inc index)))))))

(defn model-logic [{:keys [path new-value] :as tx-data} root-cursor]
  ;;(:path :old-value :new-value :old-state :new-state)
  (cond
   (= path [:filter :text])
   (filter-taxonomy new-value root-cursor)))

(defn map-component [model owner]
  (reify
    om/IShouldUpdate ;; This component is controlled from D3. We don't ever want to update it.
    (should-update [_ next-props next-state] false)
    om/IRender
    (render [_]
      (log :map-component)
      (dom/div #js {:id "map"}
               (dom/svg #js {:height (:height svg-dim), :width (:width svg-dim)})))
    om/IDidMount
    (did-mount [_]
      (let [svg (-> js/d3
                (.select "#map svg")
                (.on "click" prevent-zoom-on-drag true))]
        (draw-map svg)
        (get-birds model)))))

(defn app [model owner]
  (reify
    om/IRender
    (render [_]
      (dom/div nil
               (om/build historian model)
               (dom/div #js {:id "species"}
                        (om/build species-filter (:filter model))
                        (om/build species-list (:taxonomy model)))
               (om/build date-slider (:month-yr model))
               (om/build map-component model)))))



(defn ^:export start []
  ;; (add-watch model ::model-watch watch-model)
  (om/root app model {:target (.getElementById js/document "main")
                      :tx-listen model-logic})

  #_(let [svg (-> js/d3
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



    ;; (secretary/set-config! :prefix "#")
    ;; (events/listen history EventType.NAVIGATE
    ;;                (fn [e] (secretary/dispatch! (.-token e))))
    ;; (.setEnabled history true)


    ))
