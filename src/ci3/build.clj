(ns ci3.build
  (:require [clojure.java.shell :as sh]
            [ci3.k8s :as k8s]
            [clj-yaml.core :as yaml]
            [clojure.walk :as walk]
            [cheshire.core :as json]
            [clojure.string :as str]))

(defmulti execute (fn [st] (when-let [tp (:type st)] (keyword tp))))

(defmethod execute
  :docker
  [{cmd :command img :image}]
  (println "Execute" "docker build -t" img)
  (sh/sh "docker" "build" "-t" img "."))

(defmethod execute
  :lein
  [{cmd :command}]
  (println "Execute" "lein" cmd)
  (sh/sh "bash" "-c" (str  "lein " cmd)))

(defmethod execute
  :default
  [{img :image cmd :command}]
  (println "Execute: docker" "run" "-rm" "-t" img cmd)
  (apply sh/sh "docker" "run" "--rm" "-t" img (str/split cmd #"\s+")))

(defn build [build cb]
  (loop [[st & sts] (:pipeline build)]
    (when st
      (cb :step build st)
      (let [res (execute st)]
        (println res)
        (if-not (= 0 (:exit res))
          (println (:err res))
          (recur sts)))))
  (cb :finish build)
  (println "Done"))
