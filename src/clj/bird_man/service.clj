(ns bird-man.service
    (:require [io.pedestal.service.http :as bootstrap]
              [io.pedestal.service.http.route :as route]
              [io.pedestal.service.http.body-params :as body-params]
              [io.pedestal.service.http.route.definition :refer [defroutes]]
              [io.pedestal.service.interceptor :refer [defon-request]]
              [io.pedestal.service.log :as log]
              [hiccup.page :as page]
              [hiccup.form :as form]
              [ring.util.response :as ring-resp]
              [ring.util.codec :as ring-codec]
              [datomic.api :as d :refer (q db)]))

(defn home-page [request]
  (ring-resp/response
    (page/html5
      {:lang "en"}
      [:head
       [:meta {:charset "utf-8"}]
       [:title "Frequency Map"]]
      [:body
       (page/include-css "/stylesheets/main.css")
       (page/include-css "/stylesheets/d3.slider.css")
       (page/include-js "/javascript/goog/base.js")
       (page/include-js "/javascript/d3.v3.js")
       (page/include-js "/javascript/topojson.js")
       (page/include-js "/javascript/client-dev.js")
       (page/include-js "/javascript/colorbrewer.js")
       (page/include-js "/javascript/d3.slider.js")
       [:script "goog.require('bird_man.client');"]
       [:script "bird_man.client.start_client();"]])))

(defonce datomic-connection nil)

(defn add-datomic-conn [request]
  (assoc request :datomic-conn datomic-connection))

(defon-request datomic-conn
  "Add a conn reference to each request"
  add-datomic-conn)

(defn species-index [{conn :datomic-conn :as request}]
  (let [db (db conn)]
    (->> (d/q '[:find ?e
                :where
                [?e :taxon/order _]] db)
         (map first)
         (map (partial d/entity db))
         (sort-by :taxon/common-name)
         (ring-resp/response))))

(defn countywise-frequencies [{conn :datomic-conn :as request}]
  (ring-resp/response
    (let [{:keys [taxon year-month]} (:path-params request) ]
      (map zipmap
           (repeat [:county :state :total :sightings])
           (q '[:find ?county ?state (sum ?count) (count ?e)
                :in $ ?taxon ?ym
                :where
                [?t :taxon/order ?taxon]
                [?e :sighting/taxon ?t]
                [?e :sighting/month-yr ?ym]
                [?e :sighting/state-code ?state]
                [?e :sighting/count ?count]
                [?e :sighting/county ?county]]
              (db conn)
              taxon
              year-month) ))))

(defroutes routes
  [[["/" {:get home-page}
     ;; Set default interceptors for /about and any other paths under /
     ^:interceptors [(body-params/body-params) datomic-conn bootstrap/html-body]
     ["/species" {:get species-index}
       ^:interceptors [bootstrap/json-body]
      ["/:taxon/:year-month" 
       ^:constraints {:taxon #"\d+\.?\d+":year-month #"\d{4}/\d{2}"}
       {:get countywise-frequencies}]]]]])

;; Consumed by bird-man.server/create-server
;; See bootstrap/default-interceptors for additional options you can configure
(def service {:env :prod
              ;; You can bring your own non-default interceptors. Make
              ;; sure you include routing and set it up right for
              ;; dev-mode. If you do, many other keys for configuring
              ;; default interceptors will be ignored.
              ;; :bootstrap/interceptors []
              ::bootstrap/routes routes

              ;; Uncomment next line to enable CORS support, add
              ;; string(s) specifying scheme, host and port for
              ;; allowed source(s):
              ;;
              ;; "http://localhost:8080"
              ;;
              ;;::bootstrap/allowed-origins ["scheme://host:port"]

              ;; Root for resource interceptor that is available by default.
              ::bootstrap/resource-path "/public"

              ;; Either :jetty or :tomcat (see comments in project.clj
              ;; to enable Tomcat)
              ;;::bootstrap/host "localhost"
              ::bootstrap/type :jetty
              ::bootstrap/port 8080})
