(ns ci3.agent.cache
  (:import io.minio.MinioClient)
  (:require [ci3.agent.shelk :as shelk]))

(defn client []
  (MinioClient. (System/getenv "CI3_SECRET_STORAGE_HOST")
                (System/getenv "CI3_SECRET_STORAGE_ACCESSKEY")
                (System/getenv "CI3_SECRET_STORAGE_SECRETKEY") true ))

(def bucket
  (or (System/getenv "CI3_CONFIG_CACHE_BUCKET") "ci-cache"))

;; TODO: safe read and upload
(defn archive-dir [dir to]
  (shelk/bash ["tar" "czvf" to dir ">/dev/null 2>&1"]))

(defn upload-to-bucket [file-path k]
  (println file-path k bucket
           (System/getenv "CI3_CONFIG_CACHE_BUCKET") 
           (System/getenv "CI3_SECRET_STORAGE_HOST")
           (System/getenv "CI3_SECRET_STORAGE_ACCESSKEY")
           (System/getenv "CI3_SECRET_STORAGE_SECRETKEY"))
  (try
    (.putObject (client) bucket k file-path)
    (catch Exception e
      (println "ERROR: failed to upload to bucket "
               file-path "=>" bucket "/" k
               ": " e))))

(defn download-from-bucket [file-path k]
  (try 
    (.getObject (client) bucket k file-path)
    (catch Exception e
      (println "ERROR: failed download from bucket "
               file-path "=>" bucket "/" k
               ": " e))))

(comment
  
  (def bucket "cleo-ci-cache")

  (defn client []
    (MinioClient.
     "https://storage.googleapis.com"
     "GOOGDRTIJ6PF4A7EOI3B"
     "K/8qpEBa4UoaqGmBsOXWL0dCZ9oVF+QQIsMQ/pHY"
     true ))

  (.bucketExists (client) "cleo-ci-cache")

  (upload-to-bucket "/tmp/builds" "test-cache-2")
  (download-from-bucket "/tmp/builds" "test-cache-4")

  )
