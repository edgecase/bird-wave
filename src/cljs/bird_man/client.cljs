(ns bird-man.client
  (:require-macros [cljs.core.async.macros :refer (go go-loop alt!)]
                   [kioo.om :refer [defsnippet deftemplate]])
  (:require [clojure.string :as cs]
            [clojure.walk :refer (keywordize-keys)]
            [goog.string.format :as gformat]
            [kioo.om :refer [content set-attr set-class do-> substitute listen]]
            [kioo.core :refer [handle-wrapper]]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [goog.events :as events]
            [ankha.core :as ankha]
            [cljs.core.async :as async :refer (chan put! <! timeout)]
            [bird-man.map :refer (init-axis color active-state zoom zoom-duration
                                  svg-dim state-to-activate active-attrs target
                                  prevent-zoom-on-drag init-map update-counties make-frequencies)]
            [bird-man.flickr :refer (search-query info-query first-photo attribution)])

  (:import goog.History
           goog.history.EventType))

(defn log [& args]
  (if js/window.__birdman_debug__
    (.log js/console (pr-str args))))

(defn try-with-default [m k default]
  (if (seq m) (k m) default))

(def default-taxon "2881")

(def model (atom {:current-taxon nil
                  :current-name ""
                  :time-period nil
                  ;; hard code the default taxon :(
                  :taxonomy [{:taxon/order "2881"
                              :taxon/subspecies-scientific-name ""
                              :taxon/scientific-name "Haliaeetus leucocephalus"
                              :taxon/subspecies-common-name ""
                              :taxon/common-name "Bald Eagle"}]
                  :frequencies {}
                  :photo {}}))

(defn update-map! [model]
  (let [{:keys [current-taxon time-period]} @model
        url (str "species/" current-taxon "/" time-period)]
    (when (and current-taxon time-period)
      (js/d3.json url (fn [data]
                        (om/update! model :frequencies
                                    (make-frequencies data))
                        (update-counties (:frequencies @model)))))))

(defn update-photo! [model]
  (let [{:keys [current-name]} @model
        url (search-query (.toLowerCase current-name) 1)]
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

(defn selection-name [{:keys [current-name]} owner]
  (reify
    om/IRender
    (render [_]
      (dom/h2 #js {:className "selection-name"} current-name))))

(defn fetch-attribution [e model]
  (let [photo-id (:id @model)
        secret (:secret @model)
        url (info-query photo-id secret)]
    (.preventDefault e)
    (js/d3.json url (fn [data]
                      (om/update! model :attribution (attribution data))))))

(deftemplate selection-image "templates/selection-image.html" [model owner]
  {[:#selection-image] (set-class (if (seq model) "loaded" "no-photo"))
   [:.photo] (set-attr :src (try-with-default model :url_q "/images/loading.png"))
   [:.title] (content (try-with-default model :title "No photo available"))
   [:.detail] (if (seq (:attribution model))
                (do->
                  (set-attr :href (get-in model [:attribution :url]))
                  (set-attr :onClick nil)
                  (set-class "detail fetched")
                  (content (get-in model [:attribution :by])))
                (do->
                  (set-class "detail")
                  (set-attr :onClick #(fetch-attribution % model))))})

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
               :max (dec (count dates))
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

(defn species-for-order [order taxonomy]
  (first (filter (fn [taxon]
                   (= order (:taxon/order taxon))) taxonomy)))

(defn map-component [model owner]
  (reify
    om/IShouldUpdate ;; This component is controlled from D3. We don't ever want to update it.
    (should-update [_ next-props next-state] false)
    om/IRender
    (render [_]
      (dom/div #js {:id "map"}
        (dom/svg #js {:height (:height svg-dim)
                      :width (:width svg-dim)})))
    om/IDidMount
    (did-mount [_]
      (init-map "#map svg" model)
      (get-birds model))))

(defn display-name [species]
  (first (filter not-empty [(:taxon/subspecies-common-name species) (:taxon/common-name species)])))


;;;;;;;;;;;;;;;;

(defn species-item [model owner]
  (reify
    om/IRenderState
    (render-state [_ {:keys [highlighted selected select-ch]}]
      (dom/li #js {:className (str "taxon"
                                   (if (= model highlighted) " highlighted")
                                   (if (= model selected) " active"))}
        (dom/a #js {:href (taxon-path (:taxon/order model))
                    :onClick (fn [e] (.preventDefault e) (put! select-ch @model))}
               (display-name model))))

    om/IDidUpdate
    (did-update [_ prev-props prev-state]
      (if (and (= model (om/get-state owner :highlighted))
               (not= model (:highlighted prev-state)))
        (.scrollIntoViewIfNeeded (om/get-node owner))))))

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

(defn autocomplete [model owner {:keys [select-ch control-ch filter-fn]}]
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
                highlighted-index (om/get-state owner :highlighted-index)]
            (case event
              :up (if (>= highlighted-index 0)
                    (om/update-state! owner #(assoc % :highlighted-index (dec highlighted-index))))
              :down (om/update-state! owner #(assoc % :highlighted-index (inc highlighted-index)))
              :value (om/update-state! owner #(assoc % :filter-value value, :highlighted-index -1))
              :select (let [filtered-list (filter-taxonomy @model (om/get-state owner :filter-value))
                            highlighted (try (nth filtered-list highlighted-index)
                                             (catch js/Error e))]
                        (if highlighted
                          (put! internal-select-ch highlighted)))
              nil))
          (recur))
        (go-loop []
          (let [selection (<! internal-select-ch)]
            (om/set-state! owner :selected selection)
            (put! select-ch selection))
          (recur))))

    om/IRenderState
    (render-state [_ {:keys [input-ch highlighted-index filter-value
                             selected internal-select-ch]}]
      (let [filtered-list (filter-taxonomy model filter-value)
            highlighted (try (nth filtered-list highlighted-index) (catch js/Error e))]
        (dom/div #js {:id "species"}
                 (om/build input-view model {:init-state {:input-ch input-ch}})
                 (om/build species-list model {:state {:filtered-list filtered-list
                                                       :highlighted highlighted
                                                       :selected selected
                                                       :select-ch internal-select-ch}})
                 (dom/div #js {:className "more"}
                          (dom/i #js {:className "icon-chevron-down"})))))))

(defn await-taxonomy
  "Return a channel which will receive the value of :taxonomy after it has a non-empty value"
  [model]
  (go-loop []
    (let [taxonomy (:taxonomy @model)]
      (if (not-empty taxonomy)
        taxonomy
        (do (<! (timeout 10))
            (recur))))))

;;;;;;;;;;

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
            species-ch ([result]
                        (om/update! model :current-taxon (:taxon/order result))
                        (om/update! model :current-name (display-name result))
                        (push-state model history)
                        (update-map! model)
                        (update-photo! model))
            history-ch ([{:keys [current-taxon time-period]}]
                        (log :history-ch)
                        (go
                          (let [use-defaults? (not (and current-taxon time-period))
                                taxonomy (<! (await-taxonomy model))
                                time (or time-period (first dates))
                                taxon (cond
                                       current-taxon current-taxon
                                       (empty? taxonomy) default-taxon
                                       :else (:taxon/order (rand-nth taxonomy)))]
                            (om/update! model :current-taxon taxon)
                            (om/update! model :current-name (display-name (species-for-order taxon (:taxonomy @model))))
                            (om/update! model :time-period time)
                            (update-map! model)
                            (update-photo! model)
                            (when use-defaults?
                              (push-state model history))))))
                 (recur))))

    om/IRenderState
    (render-state [_ {:keys [time-period-ch species-ch history-ch]}]
      (dom/div nil
        (om/build autocomplete (:taxonomy model)
                  {:opts {:select-ch species-ch}})
        (om/build selection-name model)
        (om/build selection-image (:photo model))
        (om/build date-slider model {:state {:time-period-ch time-period-ch}})
        (om/build map-component model)))))

(defn open-section []
  (let [section (-> js/d3
                    (.select (target))
                    (.node)
                    (.-parentNode))
        selection (.select js/d3 section)
        open (not (-> selection (.classed "closed")))]
    (-> selection
        (.classed "closed" open))))

(defn ^:export start []
  (om/root app model {:target (.getElementById js/document "main")})
  ( -> js/d3
       (.select "#how-this-works h2")
       (.on "click" open-section)))

(defn ^:export info []
  (log (dissoc @model :taxonomy :frequencies)))
