(ns ci3.agent
  (:require [clojure.java.shell :as sh]
            [ci3.k8s :as k8s]
            [ci3.build :as build]
            [clj-yaml.core :as yaml]
            [clojure.walk :as walk]
            [cheshire.core :as json]
            [clojure.string :as str]
            [clojure.java.io :as io]))

(defn build-id [] (System/getenv "BUILD_ID"))

(defn get-build [bid]
  (let [bld (k8s/find k8s/cfg :builds (str/trim bid))]
    (when-not (or bld (= "Failure" (get bld "status")))
      (throw (Exception. (str "Could not find build: " bid " or " bld))))
    (println "Got build: " bld)
    (walk/keywordize-keys bld)))

(defn checkout-project []
  (if-let [repo (System/getenv "REPOSITORY")]
    (let [res (sh/sh "git" "clone" repo "/workspace")]
      (println res)
      res)
    (throw (Exception. "Provide $REPOSITORY"))))

(defn exec [& args]
  ;; checkout project
  (let [repo (checkout-project)]
    (when-not (= 0 (:exit repo))
      (println repo)
      (throw (Exception. "Unable to clone repository"))))
  (let [bld (get-build (build-id))]
    (build/build (yaml/parse-string (slurp "/workspace/ci3.yaml") true) println))
  (System/exit 0))
