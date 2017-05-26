(ns ci3.shelk
  (:require [clojure.java.shell :as sh]
            [clojure.contrib.humanize :as humanize]
            [clojure.string :as str]))


(defn pprint [res]
  (println "$" (:command res)
           "\n in " (humanize/duration (/ (:time res) 1000) {:number-format str})
           "with status:" (:exit res))
  (when-not  (str/blank? (:out res)) (println "==STDOUT==\n" (:out res)))
  (when-not (str/blank? (:err res)) (println "==STDERR==\n" (:err res)))
  (println "---------")
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
  (bash ["ls" "-Lah"] :dir "/")
  )






