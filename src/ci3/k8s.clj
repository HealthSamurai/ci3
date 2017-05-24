(ns ci3.k8s
  (:require
   [org.httpkit.client :as http-client]
   [clj-json-patch.core :as patch]
   [clojure.walk :as walk]
   [cheshire.core :as json]))

(def default-headers
  (if-let [token (System/getenv "KUBE_TOKEN")]
    {"Authorization" (str "Bearer " token)}
    {}))


(def kube-url (or (System/getenv "KUBE_URL") "http://localhost:8001"))

(println "url:" kube-url)
(println "headers:" default-headers)

(defn url [cfg pth]
  (str kube-url "/" pth))

(defn query [cfg rt & [pth]]
  (let [res @(http-client/get
              (url cfg (str "apis/" (:apiVersion cfg) "/namespaces/" (:ns cfg) "/" (name rt) "/" pth))
              {:headers (merge default-headers {"Content-Type" "application/json"})
               :insecure? true})]
    (-> res
     :body
     (json/parse-string))))

(defn list [cfg rt] (query cfg rt))
(defn find [cfg rt id] (query cfg rt id))

(defn create [cfg rt res]
  (-> @(http-client/post
        (url cfg (str "apis/" (:apiVersion cfg) "/namespaces/" (:ns cfg) "/" (name rt)))
        {:body (json/generate-string res)
         :insecure? true
         :headers (merge default-headers {"Content-Type" "application/json"})})
      :body
      (json/parse-string)))

(defn delete [cfg rt id]
  (-> @(http-client/delete
        (url cfg (str "apis/" (:apiVersion cfg) "/namespaces/" (:ns cfg) "/" (name rt) "/" id))
        {:headers (merge default-headers {"Content-Type" "application/json"})
         :insecure? true})
      :body
      (json/parse-string)))

(defn patch [cfg rt id patch]
  (let [res (find cfg rt id)]
    (if-not (= "Failure" (get res "status"))
      (let [diff (patch/diff res (merge res (walk/stringify-keys patch)))]
        (->
         @(http-client/patch
           (url cfg (str "apis/" (:apiVersion cfg) "/namespaces/" (:ns cfg) "/" (name rt) "/" id))
           {:body (json/generate-string diff)
            :insecure? true
            :headers (merge default-headers {"Content-Type" "application/json-patch+json"})})
         :body))
      res)))


(def cfg {:apiVersion "ci3.io/v1" :ns "default"})

(comment
  (list cfg :builds)
  (find cfg :builds "dfdfdf")
  (patch cfg :builds "test-1" {:status "changed"})

  (delete cfg :builds "test-1")

  (create cfg :builds
          {:kind "Build"
           :apiVersion "ci3.io/v1"
           :metadata {:name "test-00"}})
  )

#_(query {:apiVersion "zeroci.io/v1"
        :ns "default"}
       :builds "test-1")

#_(query {:apiVersion "zeroci.io/v1"
        :ns "default"}
       :builds "test-1")
