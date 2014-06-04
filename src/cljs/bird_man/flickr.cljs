(ns bird-man.flickr
  (:require [cemerick.url :refer (url url-encode)]
            [clojure.walk :refer (keywordize-keys)]))

(def api-base-url (url "https://api.flickr.com/services/rest/"))

(def api-key-params {:api_key "bc406ff80528f231e9e38f7dccc3723f"})
(def format-params {:format "json" :nojsoncallback 1})

(defn search-params [text, num-photos]
  "Params for searching num-photos (max 500) with text"
  {:text (str "\"" text "\"")
   :per_page num-photos
   :method "flickr.photos.search"
   :sort "relevance"
   :license 4
   :extras "owner_name,url_q"})

(defn info-params [photo-id, secret]
  "Params for fetching details of photo with photo-id and optional secret"
  {:method "flickr.photos.getInfo"
   :photo_id photo-id
   :secret secret})

(defn make-request [func & args]
  (let [query (into {} [api-key-params
                        format-params
                        (apply func args)])]
    (-> api-base-url
        (assoc :query query)
        str)))

(defn search-query [text, num-photos]
  (make-request search-params text num-photos))

(defn info-query [photo-id, secret]
  (make-request info-params photo-id secret))

(defn first-photo [photos]
  (-> photos
      (js->clj)
      (keywordize-keys)
      (:photos)
      (:photo)
      (first)))

