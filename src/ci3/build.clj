(ns ci3.build
  (:require [clojure.java.shell :as sh]
            [ci3.k8s :as k8s]
            [clj-yaml.core :as yaml]
            [clojure.walk :as walk]
            [cheshire.core :as json]
            [clojure.string :as str]))

(defmulti execute (fn [st] (when-let [tp (:type st)] (keyword tp))))

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

;; (archive-dir "/Users/nicola/.m2" "mvn")

(def tk "ya29.GlxVBN_9Ct8ddZZyKHXj4E5IcAnZo5_HLj4hzn5qWfnJci97Z2cXLYblRzLGrNQf8cC6fQv79coFOChBj6f3vMYnYstUUWI7bsOlgHIhowvULonddCn13fCBGjJUPw")


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
