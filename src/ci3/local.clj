(ns ci3.local
  (:require [clojure.java.shell :as sh]
            [ci3.k8s :as k8s]
            [ci3.build :as build]
            [clj-yaml.core :as yaml]
            [clojure.walk :as walk]
            [cheshire.core :as json]
            [clojure.string :as str])
  (:gen-class))

(defn run [& args]
  (let [b (yaml/parse-string (slurp "ci3.yaml") true)]
    (println (sh/sh "pwd"))
    (build/build b println)))

(defn exec [& args]
  (apply run args)
  (System/exit 0))

(comment
  (run)

)
