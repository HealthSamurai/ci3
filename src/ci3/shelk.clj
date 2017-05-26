(ns ci3.shelk
  (:require [clojure.java.shell :as sh]
            [clojure.contrib.humanize :as humanize]
            [clojure.string :as str]))


(defn pprint [res]
  (println "$" (:command res)
           "["
           (if (= 0 (:exit res))  "OK" (str "ERROR(" (:exit res) ")"))
           " in " (humanize/duration (/ (:time res) 1000000) {:number-format str})
           "]")
  (when-not (str/blank? (str/trim (:err res))) (println "STDERR:\n" (:err res)))
  (when-not (str/blank? (str/trim (:out res))) (println "STDOUT:\n" (:out res)))
  res)

(defn bash [cmd & opts]
  (let [cmd (cond (string? cmd) cmd
                  (vector? cmd) (str/join " " cmd))
        start (System/nanoTime)]
    (-> (apply sh/sh "bash" "-c" cmd opts)
        (assoc :command cmd
               :time  (- (System/nanoTime) start))
        (pprint))))

(comment
  (bash "ls" :dir "/")
  (str/trim (str/trim (:out (bash ["ls -lah"]))))
  )
