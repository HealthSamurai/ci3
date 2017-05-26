(ns ci3.build
  (:require [clojure.java.shell :as sh]
            [ci3.k8s :as k8s]
            [ci3.gcloud :as gcloud]
            [clj-yaml.core :as yaml]
            [clojure.walk :as walk]
            [cheshire.core :as json]
            [clojure.pprint :as pprint]
            [clojure.string :as str]
            [ci3.shelk :as shelk]))

(defmulti execute
  (fn [st] (when-let [tp (:type st)] (keyword tp))))

(defmethod execute
  :docker
  [{cmd :command img :image}]
  (shelk/bash ["docker" "build" "-t" img "."]))

(defmethod execute
  :lein
  [{cmd :command}]
  (shelk/bash ["lein " cmd]))


(defmethod execute
  :default
  [{img :image cmd :command}]
  (shelk/bash ["docker" "run" "--rm" "-t" img (str/split cmd #"\s+")]))

(defmulti maven-execute (fn [{cmd :command}] (keyword cmd)))

(defn archive-dir [dir to]
  (shelk/bash ["tar" "czvf" to dir ">/dev/null 2>&1"]))

(defn bucket []
  (or (System/getenv "CACHE_BUCKET") "ci3-cache"))

(defn upload-bucket-object-url [k]
  (str "https://www.googleapis.com/upload/storage/v1/b/"
       (bucket)
       "/o/?uploadType=media\\&name=" k ".tar.gz\\&" k "=mvn"))

(defn upload-to-bucket [access-token file-path k]
  (shelk/bash
   (str  "curl -X POST "
         " --data-binary @" file-path " "
         " -H 'Authorization: Bearer " access-token "' "
         (upload-bucket-object-url k))))

(defn download-bucket-object-url [k]
  (str "https://www.googleapis.com/download/storage/v1/b/"
       (bucket)
       "/o/" k ".tar.gz?alt=media"))

(defn download-from-bucket [access-token file-path k]
  (shelk/bash
   (str  "curl "
         "-H 'Authorization: Bearer " access-token "' "
         (download-bucket-object-url k)
         " -o " file-path)))

(defmethod maven-execute
  :save-cache
  [{k :key}]
  (let [tmp-file (str "/tmp/" k  ".tar.gz")]
    (archive-dir "/root/.m2" tmp-file)
    (shelk/bash ["ls -lah" tmp-file])
    (upload-to-bucket (gcloud/get-access-token) tmp-file k))
  {:exit 0})

(defmethod maven-execute
  :restore-cache
  [{k :key}]
  (let [tmp-file (str "/tmp/" k  ".tar.gz")]
    (download-from-bucket (gcloud/get-access-token) tmp-file k)
    (shelk/bash ["tar" "xzvf" tmp-file ">/dev/null 2>&1"] :dir "/"))
  {:exit 0})

(defmethod execute
  :maven
  [args]
  (maven-execute args))

(defmethod execute
  :bash
  [{cmd :command env :env }]
  (println "Execute" "bash" cmd)
  (let [env (->> (or env {})
                 (reduce-kv (fn [acc k v] (str acc (name k) "=" v " ")) "")
                 str/trim) ]
    (shelk/bash (str env " bash -c '" cmd "'"))))

(defn build [build]
  (loop [[st & sts] (:pipeline build)]
    (if st
      (let [res (execute st)]
        (if-not (= 0 (:exit res))
          (println "ERROR!") 
          (recur sts)))
      (println "DONE!"))))
