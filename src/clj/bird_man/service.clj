(ns bird-man.service
    (:require [io.pedestal.service.http :as bootstrap]
              [io.pedestal.service.http.route :as route]
              [io.pedestal.service.http.body-params :as body-params]
              [io.pedestal.service.http.route.definition :refer [defroutes]]
              [io.pedestal.service.interceptor :refer [definterceptor on-response defon-request]]
              [io.pedestal.service.http.ring-middlewares :as middlewares]
              [io.pedestal.service.log :as log]
              [clojure.java.io :as io]
              [ring.util.response :as ring-resp]
              [ring.util.codec :as ring-codec]
              [datomic.api :as d :refer (q db)]
              [net.cgrand.enlive-html :refer (template deftemplate set-attr)]))

(defonce datomic-connection nil)
(defonce env :dev)

(defn connect-datomic [uri]
  (alter-var-root #'datomic-connection (constantly (d/connect uri))))

(defn set-env [e]
  (alter-var-root #'env (constantly e)))

(defn add-datomic-conn [request]
  (assoc request :datomic-conn datomic-connection))

(defon-request datomic-conn
  "Add a conn reference to each request"
  add-datomic-conn)

(defn cache-control
  "Return response interceptor which adds Cache-Control header to response, when appropriate.
   Will not override header when already set."
  [max-age]
  (on-response
   (fn [{:keys [status headers] :as response}]
     (if (= 200 status)
       (let [val (if (= :no-cache max-age)
                   "no-cache"
                   (str "public, max-age=" max-age))]
         (assoc response :headers
                (merge {"Cache-Control" val} headers)))
       response))))

(deftemplate home-template "templates/index.html" [env]
  [:#client-script] (set-attr :src (case env
                                     :prod "/javascript/client.js"
                                     :advanced "/javascript/advanced/client.js"
                                     "/javascript/build/client-dev.js")))

(defn home-page [request]
  (ring-resp/response
   (apply str (home-template env))))

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

(defn am-i-alive [request]
  (ring-resp/response "OK"))

(defroutes routes
  [[["/" {:get home-page}
     ;; Set default interceptors for any paths under /
     ^:interceptors [(body-params/body-params) datomic-conn bootstrap/html-body]

     ["/species" {:get species-index}
      ^:interceptors [bootstrap/json-body (cache-control 300)]

      ["/:taxon/:year-month"
       ^:constraints {:taxon #"\d+\.?\d+":year-month #"\d{4}/\d{2}"}
       ^:interceptors [(cache-control 99000000)]
       {:get countywise-frequencies}]]]

    ["/health-check" {:get am-i-alive}]]])

;; Consumed by bird-man.server/create-server
(def service
  {
   ::bootstrap/interceptors
   [bootstrap/log-request
    ;; (cors/allow-origin ["scheme://host:port"])
    (cache-control :no-cache)
    bootstrap/not-found
    (middlewares/content-type {:mime-types {}})
    route/query-params
    (route/method-param "_method")
    (middlewares/resource "/public")
    ;; (middlewares/file "/files")
    (route/router routes)]
   :env :prod
   ::bootstrap/type :jetty
   ::bootstrap/port 8080})
