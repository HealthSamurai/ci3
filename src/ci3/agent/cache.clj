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
  (.putObject (client) bucket k file-path))

(defn download-from-bucket [file-path k]
  (.getObject (client) bucket k file-path))

(comment
  (.bucketExists (client) bucket)

  )
