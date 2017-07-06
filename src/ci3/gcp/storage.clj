(ns ci3.gcp.storage
  (:require [clojure.java.shell :as sh]
            [ci3.k8s :as k8s]
            [ci3.gcp.gcloud :as gcloud]
            [clj-yaml.core :as yaml]
            [clojure.walk :as walk]
            [cheshire.core :as json]
            [clojure.pprint :as pprint]
            [clojure.string :as str]))

(defn bucket []
  (or (System/getenv "CACHE_BUCKET") "ci3-cache"))

(defn upload-bucket-object-url [k]
  (str "https://www.googleapis.com/upload/storage/v1/b/"
       (bucket)
       "/o/?uploadType=media\\&name=" k ".tar.gz\\&" k "=mvn"))

(defn upload-to-bucket [access-token file-path k]
  (println (sh/sh "bash" "-o" "xtrace"
                  "-c" (str  "curl -X POST "
                             " --data-binary @" file-path " "
                             " -H 'Authorization: Bearer " access-token "' "
                             (upload-bucket-object-url k)))))

(defn download-bucket-object-url [k]
  (str "https://www.googleapis.com/download/storage/v1/b/"
       (bucket)
       "/o/" k ".tar.gz?alt=media"))

(defn download-from-bucket [access-token file-path k]
  (println
   (sh/sh "bash"
          "-o" "xtrace"
          "-c" (str  "curl "
                     "-H 'Authorization: Bearer " access-token "' "
                     (download-bucket-object-url k)
                     " -o " file-path))))
