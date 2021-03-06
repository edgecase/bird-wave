(ns bird-wave.client
  (:require-macros [cljs.core.async.macros :refer (go go-loop alt!)])
  (:require [clojure.string :as cs]
            [clojure.walk :refer (keywordize-keys)]
            [goog.string.format :as gformat]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [goog.events :as events]
            [cljs.core.async :as async :refer (chan put! <! timeout)]
            [cljsjs.d3]
            [bird-wave.map :refer (init-axis color active-state zoom zoom-duration
                                  svg-dim state-to-activate active-attrs target
                                  prevent-zoom-on-drag init-map update-map make-frequencies)]
            [bird-wave.flickr :refer (search-query info-query first-photo attribution)]
            [bird-wave.util :refer (log try-with-default lowercase index-of analytic-event get-clj)])

  (:import goog.History
           goog.history.EventType))

(def model (atom {:current-taxon nil
                  :current-name ""
                  :time-period nil
                  :taxonomy []
                  :frequencies {}
                  :photo {}
                  :loading #{}
                  :screen-size "lg"}))

(defn watch-screen-size [model]
  (let [size-handler (fn [size]  #(swap! model assoc :screen-size size))]
    (-> js/enquire
      (.register "screen and (min-width: 0px) and (max-width: 520px)"    (size-handler "xs"))
      (.register "screen and (min-width: 521px) and (max-width: 768px)"  (size-handler "sm"))
      (.register "screen and (min-width: 769px) and (max-width: 1024px)" (size-handler "md"))
      (.register "screen and (min-width: 1025px)"                        (size-handler "lg")))))

(defn update-map! [model]
  (let [{:keys [current-taxon time-period screen-size]} @model
        lg-screen (= screen-size "lg")
        by (if lg-screen "county" "state")
        url (str "species/" current-taxon "/" time-period "?by=" by)]
    (when (and current-taxon time-period)
      (om/transact! model :loading #(conj % url))
      (get-clj url (fn [data]
                     (om/update! model :frequencies
                                 (make-frequencies by data))
                     (om/transact! model :loading #(disj % url))
                     (update-map by (:frequencies @model)))))))

(defn update-photo! [model]
  (let [{:keys [current-name]} @model
        query-str (apply str (interpose " " (re-seq #"\w{2,}" current-name)))
        url (search-query (lowercase query-str) 1)]
    (js/d3.json url (fn [data]
                      (om/update! model :photo (first-photo data))))))

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

(defn selection-name [model owner]
  (om/component
    (dom/h2 #js {:className "selection-name"} model)))

(defn fetch-attribution [e model]
  (let [photo-id (:id @model)
        secret (:secret @model)
        url (info-query photo-id secret)]
    (.preventDefault e)
    (analytic-event {:category "attribution" :action "request-attribution" :label photo-id})
    (js/d3.json url (fn [data]
                      (om/update! model :attribution (attribution data))))))

(defn get-birds [model]
  (let [url "/species"]
    (om/transact! model :loading #(conj % url))
    (get-clj url
             (fn [data]
               (om/transact! model :loading #(disj % url))
               (om/update! model :taxonomy data)))))

(defn filter-taxonomy
  "Return a seq of species which partially match the filter-text"
  [taxonomy filter-text]
  (let [filter-re (re-pattern (str ".*" (lowercase filter-text) ".*"))]
    (filter (fn [taxon]
              (let [species-name (lowercase (str (:taxon/subspecies-common-name taxon)
                                                 (:taxon/common-name taxon)))]
                (re-find filter-re species-name)))
            taxonomy)))

(defn species-for-order
  "Return first taxon which matches based on order"
  [order taxonomy]
  (first (filter (fn [taxon]
                   (= order (:taxon/order taxon))) taxonomy)))

(defn display-name
  "Return the most descriptive name we have for the species"
  [species]
  (->> [(:taxon/subspecies-common-name species) (:taxon/common-name species)]
       (filter not-empty)
       (first)))

(defn month-name [time-period]
  "Returns a funtion which formats the time-period string (YYYY/MM) as a month name"
  (let [time-bits (cs/split time-period "/")
        month (js/parseInt (last time-bits))
        year (js/parseInt (first time-bits))]
    ((.format (. js/d3 -time) "%B") (js/Date. year (dec month) 1))))

(defn await-taxonomy
  "Return a channel which will receive the value of :taxonomy after it has a non-empty value"
  [model]
  (go-loop []
    (let [taxonomy (:taxonomy @model)]
      (if (not-empty taxonomy)
        taxonomy
        (do (<! (timeout 10))
            (recur))))))

(defn selection-image [model owner]
  (reify
    om/IRender
    (render [_]
      (dom/div #js {:id "selection-image"
                    :className (if (seq model) "loaded" "no-photo")}
         (dom/img #js {:className "photo"
                       :src (try-with-default model :url_q "/images/loading.png")}
            (dom/div #js {:className "attribution"}
               (dom/h3 #js {:className "title"}
                       (try-with-default model :title "No photo available"))
               (dom/div #js {:className "by"}
                  (dom/span nil "Source: Flickr")
                  (dom/img #js {:className "icon" :src "/images/cc.svg"})
                  (dom/img #js {:className "icon" :src "/images/by.svg"})
                  (dom/br nil)
                  (if (seq (:attribution model))
                    (dom/a #js {:className "detail fetched"
                                :href (get-in model [:attribution :url])
                                :target "_blank"}
                           (get-in model [:attribution :by]))
                    (dom/a #js {:className "detail"
                                :href "#"
                                :onClick #(fetch-attribution % model)}
                           "view attribution")))))))))

(def dates #js ["2012/12" "2013/01" "2013/02" "2013/03" "2013/04" "2013/05" "2013/06"
                "2013/07" "2013/08" "2013/09" "2013/10" "2013/11"])

(defn date-slider [model owner]
  (reify
    om/IRender
    (render [_]
      (let [val (if-let [date (:time-period model)]
                  (.indexOf dates date)
                  0)]
        (dom/div #js {:id "slider"}
          (dom/div #js {:id "date-input"}
            (dom/input #js
              {:type "range"
               :min 0
               :max (dec (count dates))
               :value val
               :onChange (fn [e] (put! (om/get-state owner :time-period-ch)
                                       (get dates (js/parseInt (.. e -target -value)))))}))
          (dom/svg #js {:width "100%"}
            (dom/g #js {:className "axis"})))))
    om/IDidMount
    (did-mount [_]
      (init-axis ".axis"))))

(defn update-month! [model owner func]
  (let [current-position (.indexOf dates model)
        next-month (get dates (func (js/parseInt current-position)))
        time-period-ch (om/get-state owner :time-period-ch)]
    (when-not (nil? next-month) (put! time-period-ch next-month))))

(defn date-plus [model owner]
  (reify
    om/IRender
    (render [_]
      (dom/span #js {:id "date-plus"
                     :onClick #(update-month! model owner inc)} "+"))))

(defn date-minus [model owner]
  (reify
    om/IRender
    (render [_]
      (dom/span #js {:id "date-minus"
                     :onClick #(update-month! model owner dec)} "-"))))

(defn date-select [model owner]
  (reify
    om/IRender
    (render [_]
      (dom/div #js {:id "slider"}
        (dom/div #js {:id "date-select"}
          (om/build date-minus (:time-period model) {:state {:time-period-ch (om/get-state owner :time-period-ch)}})
          (apply dom/select #js
                 {:value (:time-period model)
                  :onChange #(put! (om/get-state owner :time-period-ch) (.. % -target -value))}
                 (map #(dom/option #js {:value %} (month-name %)) dates))
          (om/build date-plus (:time-period model) {:state {:time-period-ch (om/get-state owner :time-period-ch)}}))))))

(defn map-component
  "Render container for map which will be controlled by D3."
  [model owner]
  (reify
    om/IShouldUpdate
    (should-update [_ next-props next-state]
      (not (= (:screen-size next-props) (:screen-size (om/get-props owner)))))

    om/IRender
    (render [_]
      (dom/div #js {:id "map"}
        (dom/svg #js {:height (:height svg-dim)
                      :width (:width svg-dim)})))

    om/IDidMount
    (did-mount [_]
      (init-map "#map svg" model))

    om/IDidUpdate
    (did-update [_ prev-props prev-state]
      (init-map "#map svg" model)
      (js/setTimeout #(update-map! model) 0))))


(defn species-item [model owner]
  (reify
    om/IRenderState
    (render-state [_ {:keys [highlighted selected select-ch]}]
      (dom/li #js {:className (str "taxon"
                                   (if (= model highlighted) " highlighted")
                                   (if (= model selected) " active"))}
        (dom/a #js {:href (taxon-path (:taxon/order model))
                    :onClick (fn [e] (.preventDefault e) (put! select-ch model))}
               (display-name model))))

    om/IDidUpdate
    (did-update [_ prev-props prev-state]
      (when (and (= model (om/get-state owner :highlighted))
               (not= model (:highlighted prev-state)))
        (try
          (.scrollIntoViewIfNeeded (om/get-node owner))
          (catch js/Error e))))))

(defn species-list [model owner]
  (reify
    om/IRenderState
    (render-state [_ {:keys [filtered-list] :as state}]
      (apply dom/ul nil
             (om/build-all species-item filtered-list
                           {:state (select-keys state [:highlighted :selected :select-ch])})))))

(defn input-view [model owner]
  (reify
    om/IRenderState
    (render-state [_ {:keys [input-ch filter-value]}]
      (dom/input
        #js {:type "text"
             :autoComplete "off"
             :spellCheck "false"
             :className "form-control"
             :placeholder "search for species"
             :value filter-value
             :onChange #(put! input-ch [:value (.. % -target -value)])
             :onKeyDown (fn [e]
                          (let [key-code (.-keyCode e)
                                ctrl? (.-ctrlKey e)
                                message (cond
                                         (= 40 key-code) [:down]
                                         (and ctrl? (= 78 key-code)) [:down]

                                         (= 38 key-code) [:up]
                                         (and ctrl? (= 80 key-code)) [:up]

                                         (= 13 key-code) [:select])]
                            (when message
                              (.preventDefault e)
                              (put! input-ch message))))}
        (dom/i #js {:className "icon-search"})))

    om/IDidMount
    (did-mount [_]
      (.focus (om/get-node owner)))))

(defn filterlist [model owner {:keys [select-ch control-ch filter-fn]}]
  (reify
    om/IInitState
    (init-state [_]
      {:input-ch (chan)
       :internal-select-ch (chan)
       :filter-value ""
       :highlighted-index -1
       :selected nil})

    om/IWillMount
    (will-mount [_]
      (let [input-ch (om/get-state owner :input-ch)
            internal-select-ch (om/get-state owner :internal-select-ch)]
        (go-loop []
          (let [[event value] (<! input-ch)
                highlighted-index (om/get-state owner :highlighted-index)
                filtered-list (filter-taxonomy @model (om/get-state owner :filter-value))]
            (case event
              :up (when (> highlighted-index 0)
                    (om/update-state! owner #(assoc % :highlighted-index (dec highlighted-index))))
              :down (when (< (inc highlighted-index) (count filtered-list))
                      (om/update-state! owner #(assoc % :highlighted-index (inc highlighted-index))))
              :value (om/update-state! owner #(assoc % :filter-value value, :highlighted-index -1))
              :select (if-let [highlighted (try (nth filtered-list highlighted-index) (catch js/Error e))]
                        (put! internal-select-ch highlighted))
              nil))
          (recur))
        (go-loop []
          (let [selection (<! internal-select-ch)
                filtered-list (filter-taxonomy @model (om/get-state owner :filter-value))
                highlighted-index (index-of selection filtered-list -1)]
            (om/set-state! owner :selected selection)
            (om/set-state! owner :highlighted-index highlighted-index)
            (put! select-ch selection))
          (recur))))

    om/IRenderState
    (render-state [_ {:keys [input-ch highlighted-index filter-value
                             selected internal-select-ch]}]
      (let [filtered-list (vec (filter-taxonomy model filter-value))
            highlighted (try (nth filtered-list highlighted-index) (catch js/Error e))]
        (dom/div #js {:id "species"}
                 (om/build input-view model {:init-state {:input-ch input-ch}})
                 (om/build species-list model {:state {:filtered-list filtered-list
                                                       :highlighted highlighted
                                                       :selected selected
                                                       :select-ch internal-select-ch}})
                 (dom/div #js {:className "more"}
                          (dom/i #js {:className "icon-chevron-down"})))))))

(defn loading-indicator [model owner]
  (let [loading? (not (empty? model))]
    (om/component
      (dom/div #js {:className (str "spinner" (when loading? " in"))}
               (dom/span nil "L")
               (dom/span nil "O")
               (dom/span nil "A")
               (dom/span nil "D")
               (dom/span nil "I")
               (dom/span nil "N")
               (dom/span nil "G")
               (dom/span nil "…")))))

(defn loading-overlay [model owner]
  (let [loading? (not (empty? model))]
    (om/component
      (dom/div #js {:className (str "overlay" (when loading? " in"))}))))

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
                            (update-map! model)
                            (analytic-event {:category "time-period" :action "month-change" :label new-time-period}))
            species-ch ([result]
                        (om/update! model :current-taxon (:taxon/order result))
                        (om/update! model :current-name (display-name result))
                        (push-state model history)
                        (update-map! model)
                        (update-photo! model)
                        (analytic-event {:category "species" :action "species-change" :label (display-name result)}))
            history-ch ([{:keys [current-taxon time-period]}]
                        (go
                          (let [use-defaults? (not (and current-taxon time-period))
                                taxonomy (<! (await-taxonomy model))
                                time (or time-period (first dates))
                                taxon (or current-taxon (:taxon/order (rand-nth taxonomy)))]
                            (om/update! model :current-taxon taxon)
                            (om/update! model :current-name (display-name (species-for-order taxon taxonomy)))
                            (om/update! model :time-period time)

                            (update-map! model)
                            (update-photo! model)
                            (when use-defaults?
                              (push-state model history))))))
                 (recur))))

    om/IRenderState
    (render-state [_ {:keys [time-period-ch species-ch history-ch]}]
      (dom/div nil
        (om/build selection-name (:current-name model))
        (om/build loading-overlay (:loading model))
        (om/build loading-indicator (:loading model))
        (if (contains? #{"lg" "md"} (:screen-size model))
          (om/build date-slider model {:state {:time-period-ch time-period-ch}})
          (om/build date-select model {:state {:time-period-ch time-period-ch}}))
        (om/build map-component model)
        (when (= (:screen-size model) "lg") (om/build selection-image (:photo model)))
        (om/build filterlist (:taxonomy model)
                  {:opts {:select-ch species-ch}})))

    om/IDidMount
    (did-mount [_]
      (get-birds model))))

(defn open-section []
  (let [section (-> js/d3
                    (.select (target))
                    (.node)
                    (.-parentNode))
        selection (.select js/d3 section)
        is-open (not (-> selection (.classed "closed")))]
    (-> selection
        (.classed "closed" is-open))
    (analytic-event {:category "how-this-works" :action "click" :label (if is-open "close" "open")})))

(defn ^:export start []
  (watch-screen-size model)
  (om/root app model {:target (.getElementById js/document "main")})
  ( -> js/d3
       (.select "#how-this-works h2")
       (.on "click" open-section)))

(defn ^:export info []
  (log (dissoc @model :taxonomy :frequencies)))


(defn ^:export test-transit []
  (get-clj "/species" #(log "species data" %)))
