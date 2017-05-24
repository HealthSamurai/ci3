(ns ci3.agent
  (:require [clojure.java.shell :as sh]
            [ci3.k8s :as k8s]
            [clj-yaml.core :as yaml]
            [clojure.walk :as walk]
            [cheshire.core :as json]
            [clojure.string :as str]))

(def cfg {:apiVersion "zeroci.io/v1" :ns "default"})

(defn build-id [] (System/getenv "BUILD_ID"))

(defn get-build []
  (let [cfg cfg 
        bid (build-id)
        build (k8s/find cfg :builds bid)]
    (when-not (or build (= "Failure" (get build "status")))
      (throw (Exception. (str "Could not find build: " bid " or " build))))
    (println "Got build: " build)
    (walk/keywordize-keys build)))

(defn exec [& args]
  ;; checkout project
  (build/build (get-build) println)
  (System/exit 0))
