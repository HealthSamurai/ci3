(ns ci3.cache
  (:require [ci3.shelk :as shelk]))

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
