(ns bird-man.util)

;; https://gist.github.com/jhickner/2363070
(defn debounce [func wait immediate]
  (let [timeout (atom nil)]
    (fn []
      (this-as context
               (let [args js/arguments
                     later (fn []
                             (reset! timeout nil)
                             (when-not immediate
                               (.apply func context args)))]
                 (if (and immediate (not @timeout))
                   (.apply func context args))
                 (js/clearTimeout @timeout)
                 (reset! timeout (js/setTimeout later wait)))))))
