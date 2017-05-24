(ns ci3.agent
  (:require [clojure.java.shell :as sh]
            [ci3.k8s :as k8s]
            [clojure.walk :as walk]
            [cheshire.core :as json])
  (:gen-class))

(def cfg {:apiVersion "zeroci.io/v1" :ns "default"})

(defn build-id []
  (System/getenv "BUILD_ID"))

(defn get-build []
  (let [cfg cfg 
        bid (build-id)
        build (k8s/find cfg :builds bid)]
    (when-not (or build (= "Failure" (get build "status")))
      (throw (Exception. (str "Could not find build: " bid " or " build))))

    (println "Got build: " build)
    (walk/keywordize-keys build)))

(defn build [& args]
  (let [build (get-build)
        bid (get-in build [:metadata :name])]
    (loop [[st & sts] (:pipeline build)]
      (println st)
      (when st
        (k8s/patch cfg :builds bid {:status (:name st)})
        (let [res (apply sh/sh "docker" "run" "--rm" "-t" (:image st) (:command st))]
          (println (:name st))
          (println res)
          (if-not (= 0 (:exit res))
            (println (:err res))
            (recur sts)))))
    (println (k8s/patch cfg :builds bid {:status "completed"}))
    (println "Done")))

(defn -main [& args]
  (build)
  (System/exit 0))

(comment

  (build)

  )

;; (json/parse-string (:out (sh/sh "docker" "ps" "--format" "{{ json . }}")))
;; (println (:out (sh/sh "docker" "logs" "1b8e59365667")))
