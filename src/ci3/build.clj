(ns ci3.build
  (:require [clojure.java.shell :as sh]
            [ci3.k8s :as k8s]
            [ci3.gcloud :as gcloud]
            [clj-yaml.core :as yaml]
            [clojure.walk :as walk]
            [cheshire.core :as json]
            [clojure.pprint :as pprint]
            [clojure.string :as str]
            [clojure.contrib.humanize :as humanize]
            [ci3.cache :as cache]
            [ci3.shelk :as shelk]))

(defmulti execute
  (fn [st env] (when-let [tp (:type st)] (keyword tp))))

(defmethod execute
  :docker
  [{cmd :command img :image} env]
  (merge env (shelk/bash ["docker" "build" "-t" img "."])))

(defmethod execute
  :lein
  [{cmd :command} env]
  (merge env (shelk/bash ["lein " cmd])))

(defmethod execute
  :default
  [{img :image cmd :command} env]
  (merge env (shelk/bash ["docker" "run" "--rm" "-t" img (str/split cmd #"\s+")])))

(defmulti maven-execute (fn [{cmd :command} env] (keyword cmd)))

(defmethod maven-execute
  :save-cache
  [{k :key} env]
  (let [tmp-file (str "/tmp/" k  ".tar.gz")]
    (cache/archive-dir "/root/.m2" tmp-file)
    (shelk/bash ["ls -lah" tmp-file])
    (cache/upload-to-bucket (gcloud/get-access-token) tmp-file k)
    (assoc env :exit 0)))

(defmethod maven-execute
  :restore-cache
  [{k :key} env]
  (let [tmp-file (str "/tmp/" k  ".tar.gz")]
    (cache/download-from-bucket (gcloud/get-access-token) tmp-file k)
    (shelk/bash ["tar" "xzvf" tmp-file ">/dev/null 2>&1"] :dir "/")
    (assoc env :exit 0)))

(defmethod execute
  :maven
  [args env]
  (maven-execute args env))

(defmethod execute
  :bash
  [{cmd :command} env]
  (merge env (shelk/bash cmd)))

(defmethod execute
  :env
  [step env]
  (reduce (fn [acc [k v]]
            (cond
              (map? v) (let [res (:out (shelk/bash (:command v)))]
                         (assoc-in acc [:env k] res))
              :else (assoc-in acc [:env k] v)))
          (assoc env :exit 0) (dissoc step :type)))

(defn do-step [{dir :dir :as step} env]
  (println "==============================")
  (println "STEP:" (:type step) (pr-str step))
  (println "------------------------------")
  (let [start (System/nanoTime)
        result (sh/with-sh-env (or (:env env) env)
                 (if dir
                   (sh/with-sh-dir dir
                     (execute step env))
                   (execute step env)))]
    (println "------------------------------")
    (println "step done in " (humanize/duration (/ (- (System/nanoTime) start) 1000000) {:number-format str}))

    result))

(defn build [build]
  (let [start (System/nanoTime)]
    (loop [env {:build build}
           [st & sts] (:pipeline build)]
      (if st
        (let [res (do-step st env)]
          (if-not (= 0 (:exit res))
            (println "ERROR!") 
            (recur res sts)))
        (println "==========================================\nDONE in "
                 (humanize/duration (/ (- (System/nanoTime) start) 1000000) {:number-format str}))))))
