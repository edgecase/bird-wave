(ns user
  (:require [clojure.browser.repl :as repl]))

(repl/connect "http://localhost:9000/repl")

;; for debugging
;; (om/root
;;  ankha/inspector
;;  model
;;  {:target (js/document.getElementById "inspector")})
