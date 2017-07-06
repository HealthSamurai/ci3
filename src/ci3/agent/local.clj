(ns ci3.agent.local
  (:require [clojure.java.shell :as sh]
            [ci3.k8s :as k8s]
            [clj-yaml.core :as yaml]
            [clojure.walk :as walk]
            [cheshire.core :as json]
            [clojure.string :as str])
  (:gen-class))

(defn run [& args]
  (let [b (yaml/parse-string (slurp "ci3.yaml") true)]
    (println (sh/sh "pwd"))
    (println "TODO")))

(defn exec [& args]
  (apply run args)
  (System/exit 0))

(comment
  (run)
  )
