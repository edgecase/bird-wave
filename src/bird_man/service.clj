(ns bird-man.service
    (:require [io.pedestal.service.http :as bootstrap]
              [io.pedestal.service.http.route :as route]
              [io.pedestal.service.http.body-params :as body-params]
              [io.pedestal.service.http.route.definition :refer [defroutes]]
              [io.pedestal.service.interceptor :refer [defon-request]]
              [io.pedestal.service.log :as log]
              [ring.util.response :as ring-resp]
              [datomic.api :as d :refer (q db)]))

(defn home-page
  [request]
  (ring-resp/response "Hello World!"))

(defonce datomic-connection nil)

(defn add-datomic-conn [request]
  (assoc request :datomic-conn datomic-connection))

(defon-request datomic-conn
  "Add a conn reference to each request"
  add-datomic-conn)

(defn species-index [{conn :datomic-conn :as request}]
  (let [db (db conn)]
    (->> (d/q '[:find ?e :where [?e :taxon/order _]] db)
         (map first)
         (map (partial d/entity db))
         (ring-resp/response))))

(defroutes routes
  [[["/" {:get home-page}
     ;; Set default interceptors for /about and any other paths under /
     ^:interceptors [(body-params/body-params) datomic-conn bootstrap/json-body]
     ["/species" {:get species-index}]]]])

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
