(ns ci3.build
  (:require [clojure.java.shell :as sh]
            [ci3.k8s :as k8s]
            [clj-yaml.core :as yaml]
            [clojure.walk :as walk]
            [cheshire.core :as json]
            [clojure.pprint :as pprint]
            [clojure.string :as str]))

(defmulti execute
  (fn [st] (when-let [tp (:type st)] (keyword tp))))

(defmethod execute
  :docker
  [{cmd :command img :image}]
  (println "Execute" "docker build -t" img)
  (sh/sh "docker" "build" "-t" img "."))

(defmethod execute
  :lein
  [{cmd :command}]
  (println "Execute" "lein" cmd)
  (sh/sh "bash" "-c" (str  "lein " cmd)))


(defmethod execute
  :default
  [{img :image cmd :command}]
  (println "Execute: docker" "run" "-rm" "-t" img cmd)
  (apply sh/sh "docker" "run" "--rm" "-t" img (str/split cmd #"\s+")))


(defmulti maven-execute (fn [{cmd :command}] (keyword cmd)))

(defn archive-dir [dir to]
  (println "archive: " (sh/sh "tar" "czvf" to dir)))


(def tk (System/getenv "BUCKET_KEY"))

(defmethod maven-execute
  :save-cache
  [arg]
  (println "Execute: save maven cache")
  ;;(apply sh/sh "docker" "run" "--rm" "-t" img (str/split cmd #"\s+"))
  (println "  archive .m2 dir")
  (archive-dir "/root/.m2" "/tmp/mvn.tar.gz")
  (println "lah" (sh/sh "ls" "-lah" "/tmp/mvn.tar.gz"))
  (println "cache" (sh/sh "cp" "/tmp/mvn.tar.gz" "/cache"))
  (println (sh/sh "bash" "-o" "xtrace"
                  "-c" (str  "curl -X POST "
                             " --data-binary @/tmp/mvn.tar.gz"
                             " -H 'Authorization: Bearer " tk "' "
                             "https://www.googleapis.com/upload/storage/v1/b/ci3-cache/o/?uploadType=media\\&name=mvn.tar.gz\\&key=mvn"
                             ) :dir "/tmp")))

(defmethod maven-execute
  :restore-cache
  [arg]
  (println "Execute: save maven cache")
  ;;(apply sh/sh "docker" "run" "--rm" "-t" img (str/split cmd #"\s+"))
  (println "  restore .m2 dir")
  (println (sh/sh "ls" "-lah" "/cache"))
  ;; (println "restore" (sh/sh "cp" "/cache/mvn.tar.gz" "/tmp"))

  (println
   (sh/sh "bash"
          "-o" "xtrace"
          "-c" (str  "curl "
                     "-H 'Authorization: Bearer " tk "' "
                     "https://www.googleapis.com/download/storage/v1/b/ci3-cache/o/mvn.tar.gz?alt=media"
                     " -o /tmp/mvn.tar.gz")))

  (println (sh/sh "ls" "-lah" "/tmp/mvn.tar.gz"))
  (println (sh/sh "tar" "xzvf" "/tmp/mvn.tar.gz" :dir "/"))
  (println (sh/sh "ls" "-lah" "/root/.m2"))
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
                (reduce-kv (fn [acc k v] (str acc k "=" v " ")) "")
                str/trim) ]
   (sh/sh "bash" "-c" (str env " bash -c '" cmd "'") )))

(comment

  (sh/sh "sh" "-c"  "FOO=$(git rev-parse --short HEAD) bash -c 'echo $FOO'" )

  (pprint/pprint (execute {:type "docker"
                           :command "build"
                           :image "eu.gcr.io/aidbox-next/ci32" }))
  (pprint/pprint (execute {:type "bash"
                           :env {"GIT_COMMIT" "$(git rev-parse --short HEAD)"}
                           :command "helm upgrade --set image.tag=$GIT_COMMIT -i web-hook ci3" }))
  )

(defn build [build cb]
  (loop [[st & sts] (:pipeline build)]
    (when st
      (cb :step build st)
      (let [res (execute st)]
        (println res)
        (if-not (= 0 (:exit res))
          (println (:err res))
          (recur sts)))))
  (cb :finish build)
  (println "Done"))
