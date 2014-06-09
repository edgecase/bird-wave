(ns bird-man.flickr
  (:require [cemerick.url :refer (url url-encode)]
            [clojure.walk :refer (keywordize-keys)]))

(def RESERVED "0") ;; "All Rights Reserved" url=""
(def BY-NC-SA "1") ;; "Attribution-NonCommercial-ShareAlike License" url="http://creativecommons.org/licenses/by-nc-sa/2.0/"
(def BY-NC    "2") ;; "Attribution-NonCommercial License" url="http://creativecommons.org/licenses/by-nc/2.0/"
(def BY-NC-ND "3") ;; "Attribution-NonCommercial-NoDerivs License" url="http://creativecommons.org/licenses/by-nc-nd/2.0/"
(def BY       "4") ;; "Attribution License" url="http://creativecommons.org/licenses/by/2.0/"
(def BY-SA    "5") ;; "Attribution-ShareAlike License" url="http://creativecommons.org/licenses/by-sa/2.0/"
(def BY-ND    "6") ;; "Attribution-NoDerivs License" url="http://creativecommons.org/licenses/by-nd/2.0/"
(def COMMON   "7") ;; "No known copyright restrictions" url="http://flickr.com/commons/usage/"
(def USGW     "8") ;; "United States Government Work" url="http://www.usa.gov/copyright.shtml"

(def api-base-url (url "https://api.flickr.com/services/rest/"))

(def api-key-params {:api_key "bc406ff80528f231e9e38f7dccc3723f"})
(def format-params {:format "json" :nojsoncallback 1})

(defn search-params [text, num-photos]
  "Params for searching num-photos (max 500) with text"
  {:text (str "\"" text "\"")
   :per_page num-photos
   :method "flickr.photos.search"
   :sort "relevance"
   :license BY
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

(defn attribution [photo-info]
  (let [detail  (-> photo-info
                    (js->clj)
                    (keywordize-keys)
                    (:photo))]
    {:by (first (filter not-empty (vec (vals (select-keys (:owner detail) [:realname :username :path_alias])))))
     :url (get-in detail [:urls :url 0 :_content])}))
