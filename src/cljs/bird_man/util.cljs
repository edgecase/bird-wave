(ns bird-man.util)

(defn log [& args]
  (if js/window.__birdman_debug__
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
