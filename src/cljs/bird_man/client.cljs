(ns bird-man.client
  (:require-macros [cljs.core.async.macros :refer (go go-loop alt!)])
  (:require [clojure.string :as cs]
            [clojure.walk :refer (keywordize-keys)]
            [goog.string.format :as gformat]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [goog.events :as events]
            [ankha.core :as ankha]
            [cljs.core.async :as async :refer (chan put! <! timeout)]
            [arosequist.om-autocomplete :as ac])

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
                  :time-period nil     ; selected month
                  :taxonomy []      ; all taxons
                  :sightings {}     ; sightings for selected taxon, grouped by time-period
                  }))

(defn changed? [key old new]
  (not= (get old key) (get new key)))

(declare update-counties)

(def most-recent-fetch nil)

(defn update-map! [model]
  (let [{:keys [current-taxon time-period]} @model]
    (let [url (str "species/" current-taxon "/" time-period)]
      (when (and current-taxon
                 time-period
                 (not= most-recent-fetch url))
        (js/d3.json url update-counties)))))


(defn taxon-path [taxon]
  (str "/#/taxon/" taxon))

(defn parse-route [url-fragment]
  (let [[_ route taxon-order year month] (cs/split url-fragment "/")]
    {:current-taxon taxon-order
     :time-period (when (and year month) (str year "/" month))}))

(defn historian [ch]
  (let [history (History.)]
        (events/listen history EventType.NAVIGATE
                       (fn [e]
                         (when-let [route (parse-route (.-token e))]
                           (put! ch route))))
        (.setEnabled history true)
        history))

(defn push-state [model history]
  (let [{:keys [current-taxon time-period]} @model]
    (.setToken history (str "/taxon/" current-taxon "/" time-period))))

(defn species-filter [model owner]
  (reify
    om/IDidMount
    (did-mount [_]
      (log "!!!!!!!!!!")
      (put! (om/get-state owner :focus-ch) true)
      #_(put! (om/get-state owner :value-ch) "a"))
    om/IRenderState
    (render-state [_ {:keys [value-ch highlight-ch select-ch value highlighted-index]}]
      (dom/input #js {:type "text"
                      :value value
                      :autoComplete "off"
                      :spellCheck "false"
                      :placeholder "search for species"
                      :className "typeahead"
                      :onKeyDown (fn [e]
                                   (case (.-keyCode e)
                                     40 (put! highlight-ch (inc highlighted-index))
                                     38 (put! highlight-ch (dec highlighted-index))
                                     13 (put! select-ch (or highlighted-index 0))
                                     nil))
                      :onChange #(put! value-ch (.. % -target -value))}
                 (dom/i #js {:className "icon-search"})))))

(defn species-item [model owner]
  (reify
    om/IRenderState
    (render-state [_ {:keys [item idx]}]
        (dom/li #js {:className "taxon"}
          (dom/a #js {:href (taxon-path (:taxon/order item))}
                 (first (filter not-empty [(:taxon/subspecies-common-name item) (:taxon/common-name item)])))))))

(defn species-list [model owner]
  (reify
    om/IDidMount
    (did-mount [_]
      (log :species-list :mount))
    om/IRenderState
    (render-state [_ {:keys [highlight-ch select-ch value loading? focused? suggestions highlighted-index]}]
      (log :species-list :render)
      (apply dom/ul nil
             (om/build-all species-item suggestions)))))

(defn selection-image [model owner]
  (reify
    om/IRenderState
    (render-state [_ state]
      (log :selection-image (:current-taxon model)) ;; need common name here to get pic
      (dom/img #js {:id "selection-image" :src "http://placekitten.com/200/200"}))))

(def dates #js ["2012/12" "2013/01" "2013/02" "2013/03" "2013/04" "2013/05" "2013/06"
                "2013/07" "2013/08" "2013/09" "2013/10" "2013/11"])

(defn date-slider [model owner]
  (reify
    om/IRender
    (render [_]
      (log :date-slider)
      (let [val (if-let [date (:time-period model)]
                  (.indexOf dates date)
                  0)]
        (dom/div #js {:id "slider"}
          (dom/div #js {:id "date-input"}
            (dom/input #js
              {:type "range"
               :min 0
               :max 11
               :value val
               :onChange (fn [e] (put! (om/get-state owner :time-period-ch)
                                       (get dates (js/parseInt (.. e -target -value)))))}))
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
                (let [taxonomy (map keywordize-keys (js->clj species))]
                  (om/update! model :taxonomy (vec taxonomy))))))

(defn update-counties [results]
  (log :update-counties)
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

(defn filter-taxonomy [taxonomy filter-text]
  (let [filter-re (re-pattern (str ".*" (.toLowerCase filter-text) ".*"))]
    (vec (filter (fn [taxon]
                   (let [species-name (.toLowerCase (str (:taxon/subspecies-common-name taxon)
                                                         (:taxon/common-name taxon)))]
                     (re-find filter-re species-name)))
                 taxonomy))))

(defn map-component [model owner]
  (reify
    om/IShouldUpdate ;; This component is controlled from D3. We don't ever want to update it.
    (should-update [_ next-props next-state] false)
    om/IRender
    (render [_]
      (log :map-component)
      (dom/figure #js {:id "map"}
        (dom/svg #js {:height (:height svg-dim), :width (:width svg-dim)})))
    om/IDidMount
    (did-mount [_]
      (let [svg (-> js/d3
                (.select "#map svg")
                (.on "click" prevent-zoom-on-drag true))]
        (draw-map svg)
        (get-birds model)))))

(defn ac-container [_ _ {:keys [class-name]}]
  (reify
    om/IRenderState
    (render-state [_ {:keys [input-component results-component]}]
      (dom/div #js {:id "species"}
               input-component results-component
               (dom/div #js {:className "more"}
                        (dom/i #js {:className "icon-chevron-down"}))))))

(defn display-name [item idx]
  (first (filter not-empty [(:taxon/subspecies-common-name item) (:taxon/common-name item)])))

;;;;;;;;;;;;;;;;
(defn results-view [app _ {:keys [class-name
                                  loading-view loading-view-opts
                                  render-item render-item-opts]}]
  (reify
    om/IRenderState
    (render-state [_ {:keys [highlight-ch select-ch value loading? focused? suggestions highlighted-index]}]
      (let [display? (and focused? value (not= value ""))
            display (if display? "block" "none")
            attrs #js {:className "dropdown-menu"
                       :style #js {:display display}}]
        (if loading?
          (dom/ul attrs
            (om/build loading-view app {:opts loading-view-opts}))
          (apply dom/ul attrs
            (map-indexed
              (fn [idx item]
                (om/build render-item app {:init-state
                                            {:highlight-ch highlight-ch
                                             :select-ch select-ch}
                                           :state
                                            {:item item
                                             :index idx
                                             :highlighted-index highlighted-index}
                                           :opts render-item-opts}))
              suggestions)))))))

(defn render-item [app owner {:keys [class-name text-fn]}]
  (reify
    om/IInitState
    (init-state [_]
      {:click-ch (chan)})

    om/IWillMount
    (will-mount [_]
      (let [click-ch (om/get-state owner :click-ch)
            select-ch (om/get-state owner :select-ch)]
        (go (loop []
          (<! click-ch)
          (put! select-ch (om/get-state owner :index))))))

    om/IDidMount
    (did-mount [this]
      (let [index (om/get-state owner :index)
            highlight-ch (om/get-state owner :highlight-ch)
            click-ch (om/get-state owner :click-ch)
            node (om/get-node owner)]
        (events/listen node (.-MOUSEOVER events/EventType) #(put! highlight-ch index))
        (events/listen node (.-CLICK events/EventType) #(put! click-ch true))))

    om/IRenderState
    (render-state [_ {:keys [click-ch select-ch item index highlighted-index]}]
      (let [highlighted? (= index highlighted-index)]
        (dom/li #js {:className (if highlighted? (str "active " class-name) class-name)}
          (dom/a #js {:href "#"}
            (text-fn item index)))))))
;;;;;;;;;;;;;;;;

(defn app [model owner]
  (reify
    om/IInitState
    (init-state [_]
      (let [time-period-ch (chan)
            species-ch (chan)
            history-ch (chan)]
        {:time-period-ch time-period-ch
         :species-ch species-ch
         :history-ch history-ch
         :history (historian history-ch)}))

    om/IWillMount
    (will-mount [_]
      (let [time-period-ch (om/get-state owner :time-period-ch)
            species-ch (om/get-state owner :species-ch)
            history-ch (om/get-state owner :history-ch)
            history (om/get-state owner :history)]
        (go-loop []
          (alt!
            time-period-ch ([new-time-period]
                              (om/update! model :time-period new-time-period)
                              (push-state model history)
                              (update-map! model))
            species-ch ([[idx result]]
                          (om/update! model :current-taxon (:taxon/order @result))
                          (push-state model history)
                          (update-map! model))
            history-ch ([{:keys [current-taxon time-period]}]
                          (log :history-ch)
                          (let [use-defaults? (not (and current-taxon time-period))
                                taxon (or current-taxon (:taxon/order (rand-nth (:taxonomy @model))))
                                time (or time-period (first dates))]
                            (om/update! model :current-taxon taxon)
                            (om/update! model :time-period time)
                            (update-map! model)
                            (when use-defaults?
                              (push-state model history)))))
          (recur))))

    om/IRenderState
    (render-state [_ {:keys [time-period-ch species-ch history-ch]}]
      (dom/div nil
        (om/build ac/autocomplete model
                  {:opts {:result-ch species-ch
                          :suggestions-fn (fn [value suggestions-ch cancel-ch]
                                            (put! suggestions-ch (filter-taxonomy (:taxonomy model) value)))
                          :container-view ac-container
                          :container-view-opts {}
                          :input-view species-filter
                          :input-view-opts {}
                          :results-view results-view
                          :results-view-opts {:render-item render-item
                                              :render-item-opts {:class-name "taxon"
                                                                 :text-fn display-name}}}})
        (om/build selection-image model)
        (om/build date-slider model {:state {:time-period-ch time-period-ch}})
        (om/build map-component model)))))

(defn open-section []
  (let [heading (-> js/d3 (.select (target)))
        open (not (-> heading (.classed "closed")))
        y-scroll (if open -650 650)]
    (-> heading
        (.classed "closed" open))
    (.scrollBy js/window 0 y-scroll))) ;; mind-blown.gif

(defn ^:export start []
  (om/root app model {:target (.getElementById js/document "main")})
  ( -> js/d3
       (.select ".content h2")
       (.on "click" open-section)))

(defn ^:export info []
  (log (dissoc @model :taxonomy)))
