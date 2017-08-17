(ns ci3.agent.core
  (:require [clojure.java.shell :as sh]
            [ci3.telegram :as telegram]
            [ci3.k8s :as k8s]
            [ci3.gcp.gcloud :as gcloud]
            [clj-yaml.core :as yaml]
            [clojure.walk :as walk]
            [clojure.spec.alpha :as s]
            [unifn.core :as u]
            [unifn.env :as e]
            [environ.core :as environ]
            [cheshire.core :as json]
            [clojure.string :as str]
            [clojure.contrib.humanize :as humanize]
            [ci3.agent.cache :as cache]
            [ci3.agent.shelk :as shelk]
            [clojure.string :as str]
            [ci3.build.core :as build]
            [ci3.repo.interface :as interf]
            [clojure.tools.logging :as log]))

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
(re-find #"^/" "/")

(defn do-step [{dir :dir :as step} {root ::root-dir :as env}]
  (println "==============================")
  (println "STEP:" (:type step) (pr-str step))
  (println "------------------------------")
  (let [start (System/nanoTime)
        dir (if (re-find #"^/" (or dir ""))
              dir
              (str root "/" dir))
        result (sh/with-sh-env (or (:env env) {})
                   (sh/with-sh-dir dir (execute step env)))]
    (println "------------------------------")
    (println "step done in " (humanize/duration (/ (- (System/nanoTime) start) 1000000) {:number-format str}))

    result))

(defn update-status [build]
  (let [ ;;gh-status  (gh/set-status build)
        id (get-in build [:metadata :name])]
    (k8s/patch k8s/cfg :builds id
               {  ;; :gh-status gh-status
                :status (:status build) })) )

(def base-url (or (environ/env :base-url) "http://cleo-ci.health-samurai.io/"))
(defn error [build]
  (when-not (:test build)
    (telegram/notify (str "Error build " base-url "builds/" (get-in build [:metadata :name]))))
  (update-status (assoc build :status "error")))

(defn success [build]
  (println build)
  (when-not (:test build)
    (telegram/notify (str "Success build " base-url "builds/" (get-in build [:metadata :name]))))
  (update-status (assoc build :status "success")))

(defmethod u/*fn
  ::run-build
  [{e :env build-config ::build-config build ::build :as arg}]
  (let [start (System/nanoTime)]
    {::result (loop [env (merge arg {:build build-config :env e})
                     [st & sts] (:pipeline build-config)]
                (if st
                  (let [res (do-step st env)]
                    (if-not (= 0 (:exit res))
                      (error build-config)
                      (recur res sts)))
                  (do
                    (println "==========================================\nDONE in "
                             (humanize/duration (/ (- (System/nanoTime) start) 1000000) {:number-format str}))
                    (success build))))}))

(defmethod u/*fn
  ::checkout-project
  [arg]
  (interf/checkout-project arg))

(s/def ::build-id  string?)
(s/def ::env       (s/keys :req-un [::build-id]))
(s/def ::get-build (s/keys :req-un [::env]))
(defmethod u/*fn
  ::get-build
  [{cfg :k8s {bid :build-id} :env}]
  (log/info "Get build: " bid)
  (let [bld (k8s/find cfg :builds (str/trim bid))]
    (if (= "Failure" (get bld :status))
      {::u/status :error
       ::u/message (str "Could not find build: " bid " - " bld)}
      {::build (walk/keywordize-keys bld)})))


(s/def ::repository string?)
(s/def ::build      (s/keys :req-un [::repository]))
(s/def ::get-repository (s/keys :req [::build]))
(defmethod u/*fn
  ::get-repository
  [{cfg :k8s {rid :repository} ::build}]
  (log/info "Get repo: " rid)
  (let [repo (k8s/find cfg :repositories (str/trim rid))]
    (if (= "Failure" (get repo :status))
      {::u/status :error
       ::u/message (str "Could not find repository: " rid " - " repo)}
      {:ci3.repo.core/repository (walk/keywordize-keys repo)})))

(defmethod u/*fn
  ::catch-errors
  [{error ::u/message}]
  (when error
    (log/error error)
    {:response {:status 400
                :body error}}))

(defmethod u/*fn
  ::get-build-config
  [{root ::root-dir}]
  (try
    (let [build-config (yaml/parse-string (slurp (str root "/ci3.yaml")) true)]
      (if build-config
        {::build-config build-config}
        {::u/status :error
         ::u/message (str "Wrong or empty config" build-config)}))
    (catch Exception e {::u/status :error
                        ::u/message (str "File " (str root "/ci3.yaml") " not found")})))

(defmethod u/*fn
  ::workspace
  [{repo :ci3.repo.core/repository}]
  (let [workspace (str "/workspace/" (get-in repo [:metadata :name]))
        root (or (:root repo) "")
        root (-> root
                 (str/replace  #"^/" "")
                 (str/replace  #"/$" ""))]
    {::workspace workspace
     ::root-dir  (str workspace "/" root)}))

(defn run [& [arg]]
  (log/info "Run agent")
  (u/*apply
   [::e/env
    ::get-build
    ::get-repository
    ::workspace
    ::checkout-project
    ::get-build-config
    ::e/raw-env
    ::run-build
    ::catch-errors {::u/intercept :all}]
   arg))

(comment
  (-> (run {:env {:build-id "5"}})
      ::repository ))

(defn exec  [& args]
  (run {:k8s k8s/cfg})
  (System/exit 0))


(defn local [& args])

