(ns bird-wave.util
  (:require [cognitect.transit :as transit]))

(defn log [& args]
  (if js/window.__birdwave_debug__
    (.log js/console (pr-str args))))

(defn try-with-default [m k default]
  (if (seq m) (k m) default))

(defn lowercase [s]
  (if s (.toLowerCase s) ""))

(defn index-of
  "Returns the index of the first occurrence of item in collection, or default if not found"
  ([item collection]
     (index-of item collection nil))
  ([item collection default]
     (or
      (first (keep-indexed
              (fn [index obj]
                (when (= item obj)
                  index))
              collection))
      default)))

(defn analytic-event
  "Send an analytics event to our analytics provider"
  [{:keys [category action label value] :or {:value 0 :label "Interaction"}}]
  (js/window.ga
    "send"
    #js {:hitType "event"         ;;required
         :eventCategory category  ;;required
         :eventAction action      ;;required
         :eventLabel label
         :eventValue value}))

(defn get-clj
  "Request some data from the server in transit format."
  [url callback]
  (let [reader (transit/reader :json)]
    (js/d3.xhr url "application/transit+json"
               (fn [error xhr]
                 (if error
                   (log "error requesting" url error)
                   (callback (transit/read reader (.-response xhr))))))))
