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
(defn root "/workspace")

(defn get-build [bid]
  (when bid
    (when-let [bld (k8s/find k8s/cfg :builds (str/trim bid))]
      (when-not (or bld (= "Failure" (get bld "status")))
        (throw (Exception. (str "Could not find build: " bid " or " bld))))
      (println "Got build: " (get bld "metadata"))
      (walk/keywordize-keys bld))))

(defn checkout-project []
  (when-let [full_name (System/getenv "REPOSITORY")]
    (let [token (k8s/secret "secrets" :github_token)
          repo (str "https://" token "@github.com/" full_name ".git")
          res (sh/sh "git" "clone" repo "/workspace")]
      (println res)
      res)))

(defn print-step [build step & _]
  (println "### " (:name step) (:type step)))

(defn run [& args]
  (let [repo (checkout-project)] (println repo))
  (let [id (build-id)
        build (get-build id )
        build (merge
                (yaml/parse-string (slurp "ci3.yaml") true)
                build)]
    (k8s/patch k8s/cfg :builds id
               (select-keys  build [:pipeline :environment]))
    (build/build build)))

(defn exec [& args]
  (run)
  (System/exit 0))

(comment
  (yaml/parse-string (slurp "ci3.yaml") true)
  (run))
