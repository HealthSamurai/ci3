(ns ci3.agent.core
  (:require [clojure.java.shell :as sh]
            [ci3.k8s :as k8s]
            [ci3.gcp.gcloud :as gcloud]
            [clj-yaml.core :as yaml]
            [clojure.walk :as walk]
            [unifn.core :as u]
            [unifn.env :as e]
            [cheshire.core :as json]
            [clojure.pprint :as pprint]
            [clojure.string :as str]
            [clojure.contrib.humanize :as humanize]
            [ci3.agent.cache :as cache]
            [ci3.agent.shelk :as shelk]
            [unifn.env :as e]
            [ci3.build.core :as build]))

(defmulti execute
  (fn [st env] (when-let [tp (:type st)] (keyword tp))))

(defmethod execute
  :docker
  [{cmd :command img :image} env]
  (merge env (shelk/bash ["docker" "build" "-t" img "."])))

(defmethod execute
  :lein
  [{cmd :command} env]
  (merge env (shelk/bash ["/usr/bin/lein " cmd])))

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
              (map? v) (let [res (str/trim (:out (shelk/bash (:command v))))]
                         (assoc-in acc [:env k] res))
              :else (assoc-in acc [:env k] v)))
          (assoc env :exit 0) (dissoc step :type)))

(defn do-step [{dir :dir :as step} env]
  (println "==============================")
  (println "STEP:" (:type step) (pr-str step))
  (println "------------------------------")
  (let [start (System/nanoTime)
        result (sh/with-sh-env (or (:env env) {})
                 (if dir
                   (sh/with-sh-dir dir
                     (execute step env))
                   (execute step env)))]
    (println "------------------------------")
    (println "step done in " (humanize/duration (/ (- (System/nanoTime) start) 1000000) {:number-format str}))

    result))

(defn update-status [build]
  (println "TODO")
  #_(let [gh-status  (gh/set-status build)
        id (get-in build [:metadata :name])]
    (k8s/patch k8s/cfg :builds id
               {:gh-status gh-status
                :status (:status build) })) )

(defn error [build]
  (println "ERROR!")
  (update-status (assoc build :status "error")))

(defn success [build]
  (update-status (assoc build :status "success")))

(defn run-build [build]
  (let [start (System/nanoTime)]
    (loop [env {:build build :env (get-envs)}
           [st & sts] (:pipeline build)]
      (if st
        (let [res (do-step st env)]
          (if-not (= 0 (:exit res))
            (error build)
            (recur res sts)))
        (do
          (println "==========================================\nDONE in "
           (humanize/duration (/ (- (System/nanoTime) start) 1000000) {:number-format str}))
         (success build))))))


(defn checkout-project-github []
  (when-let [full_name (System/getenv "REPOSITORY")]
    (let [token (k8s/secret "secrets" :github_token)
          repo (str "https://" token "@github.com/" full_name ".git")
          res (sh/sh "git" "clone" repo "/workspace")]
      (println res)
      res)))

(defn checkout-project [build]
  (when-let [full_name (System/getenv "REPOSITORY")]
    (let [token (k8s/secret "secrets" :github_token)
          repo (str "https://" token "@github.com/" full_name ".git")
          res (sh/sh "git" "clone" repo "/workspace")]
      (println res)
      res)))


(defmethod u/*fn
 ::checkout-project
 [{env :env build :build repo :repository}]
 {}
 )

(defmethod u/*fn
  ::get-build
  [{{bid :build-id} :env}]
  (when bid
    (when-let [bld (k8s/find k8s/cfg :builds (str/trim bid))]
      (when-not (or bld (= "Failure" (get bld :status)))
        (throw (Exception. (str "Could not find build: " bid " or " bld))))
      (println "Got build: " (get-in bld [:metadata :name]))
      {::build (walk/keywordize-keys bld)})))

(defmethod u/*fn
  ::get-repository
  [{build ::build}]
   (when-let [rid (get-in build [:payload :repository-id])]
    (when-let [bld (k8s/find k8s/cfg :repositories (str/trim rid))]
      (when-not (or bld (= "Failure" (get bld :status)))
        (throw (Exception. (str "Could not find repo: " rid " or " bld))))
      (println "Got repo: " (get-in bld [:metadata :name]))
      {::repository (walk/keywordize-keys bld)})))

(defn run [& [arg]]
  (u/*apply
   [::e/env
    ::get-build
    ::get-repository
    ;;::checkout-project
    ;;::run-build
    ]
   arg))

(comment
  (-> (run {:env {:build-id "5"}})
      ::repository ))

(defn exec  [& args]
  (run)
  (System/exit 0))



(defn local [& args])

(defn $run [& args]
  (let [repo (checkout-project)] (println repo))
  (let [id (build-id)
        build (merge (yaml/parse-string (slurp "ci3.yaml") true) build)]
    (k8s/patch k8s/cfg :builds id
               (select-keys  build [:pipeline :environment]))
    (run-build build)))
