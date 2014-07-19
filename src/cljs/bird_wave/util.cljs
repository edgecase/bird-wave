(ns bird-wave.util)

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
