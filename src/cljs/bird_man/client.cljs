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
            [arosequist.om-autocomplete :as ac]
            [bird-man.map :refer (init-axis color active-state zoom zoom-duration
                                  svg-dim state-to-activate active-attrs
                                  prevent-zoom-on-drag init-map update-counties make-frequencies)])

  (:import goog.History
           goog.history.EventType))

(defn log [& args]
  (if js/window.__birdman_debug__
    (.log js/console (pr-str args))))


(def model (atom {:current-taxon nil
                  :time-period nil     ; selected month
                  :taxonomy []      ; all taxons
                  :frequencies {}}))

(defn update-map! [model]
  (let [{:keys [current-taxon time-period]} @model
        url (str "species/" current-taxon "/" time-period)]
    (when (and current-taxon time-period)
      (js/d3.json url (fn [data]
                        (om/update! model :frequencies
                                    (make-frequencies data))
                        (update-counties (:frequencies @model)))))))


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
      (init-axis ".axis"))))





(defn get-birds [model]
  (js/d3.json "species"
              (fn [species]
                (let [taxonomy (map keywordize-keys (js->clj species))]
                  (om/update! model :taxonomy (vec taxonomy))))))




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
      (dom/div #js {:id "map"}
        (dom/svg #js {:height (:height svg-dim)
                      :width (:width svg-dim)})))
    om/IDidMount
    (did-mount [_]
      (init-map "#map svg" model)
      (get-birds model))))

(defn ac-container [_ _ {:keys [class-name]}]
  (reify
    om/IRenderState
    (render-state [_ {:keys [input-component results-component]}]
      (dom/div #js {:id "species"}
        input-component results-component))))

(defn display-name [item idx]
  (first (filter not-empty [(:taxon/subspecies-common-name item) (:taxon/common-name item)])))

;;;;;;;;;;;;;;;;

(defn results-view [app _ {:keys [class-name
                                  loading-view loading-view-opts
                                  render-item render-item-opts]}]
  (reify
    om/IRenderState
    (render-state [_ {:keys [highlight-ch select-ch value loading? focused? suggestions highlighted-index]}]
      (apply dom/ul
             #js {:className "dropdown-menu"}
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
              suggestions)))))

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
        (om/build date-slider model {:state {:time-period-ch time-period-ch}})
        (om/build map-component model)))))



(defn ^:export start []
  (om/root app model {:target (.getElementById js/document "main")}))

(defn ^:export info []
  (log (dissoc @model :taxonomy)))
