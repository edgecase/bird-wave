(ns bird-man.client
  (:require [clojure.browser.repl :as repl]))

(defn ^:export start-client []
  (repl/connect "http://localhost:9000/repl"))

(def svg-dim {:width 950 :height 500})

(def svg (-> js/d3
             (.select "body")
             (.append "svg")
             (.attr "height" (:height svg-dim))
             (.attr "width" (:width svg-dim))))

(def path ( -> js/d3 (.geo.path)))

(defn draw [err us]
  ( -> svg
       (.append "g")
       (.selectAll "path")
       (.data (aget (js/topojson.feature us (aget us "objects" "counties") #(not (= %1 %2))) "features"))
       (.enter)
       (.append "path")
       (.classed "county" true)
       (.attr "d" path))
  ( -> svg
       (.append "path")
       (.datum (js/topojson.mesh us (aget us "objects" "states")))
       (.classed "states" true)
       (.attr "d" path)))

( -> (js/queue)
     (.defer js/d3.json "data/us.json")
     (.await draw))
