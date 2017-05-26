(ns ci3.k8s
  (:import java.util.Base64)
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

(defn url [cfg pth]
  (str kube-url "/" pth))

(defn curl [cfg pth]
  (let [res @(http-client/get
              (url cfg pth)
              {:headers default-headers :insecure? true})]
    (-> res :body)))

(defn query [cfg rt & [pth]]
  (let [res @(http-client/get
              (url cfg (str (or (:prefix cfg) "apis") "/" (:apiVersion cfg) "/namespaces/" (:ns cfg) "/" (name rt) "/" pth))
              {:headers (merge default-headers {"Content-Type" "application/json"})
               :insecure? true})]
    (-> res
     :body
     (json/parse-string))))

(defn list [cfg rt] (query cfg rt))
(defn find [cfg rt id] (query cfg rt id))

(defn create [cfg rt res]
  (-> @(http-client/post
        (url cfg (str (or (:prefix cfg) "apis") "/" (:apiVersion cfg) "/namespaces/" (:ns cfg) "/" (name rt)))
        {:body (json/generate-string (walk/stringify-keys res))
         :insecure? true
         :headers (merge default-headers {"Content-Type" "application/json"})})
      :body
      (json/parse-string)))

(defn delete [cfg rt id]
  (-> @(http-client/delete
        (url cfg (str (or (:prefix cfg) "apis") "/" (:apiVersion cfg) "/namespaces/" (:ns cfg) "/" (name rt) "/" id))
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
           (url cfg (str (or (:prefix cfg) "apis") "/" (:apiVersion cfg) "/namespaces/" (:ns cfg) "/" (name rt) "/" id))
           {:body (json/generate-string diff)
            :insecure? true
            :headers (merge default-headers {"Content-Type" "application/json-patch+json"})})
         :body))
      res)))


(def cfg {:apiVersion "ci3.io/v1" :ns "default"})

(defn secret [name key]
  (let [cfg {:apiVersion "v1" :ns "deftest"}]
    (->
      @(http-client/get
         (str kube-url  "/api/v1/namespaces/default/secrets/" name)
         {:insecure? true
          :headers (merge default-headers {"Content-Type" "application/json-patch+json"})})
      :body (json/parse-string keyword)
      :data key
      (#(String. (.decode (Base64/getDecoder) %)))

      )))

(subs "1234" 1 (- (count "1234") 1))


(comment
  (list cfg :builds)
  (secret "ci3" :mySecret)
  (secret "ci3" :defaultSecret)
  (find cfg :builds "ci3-build-6")

  (patch cfg :builds "test-1" {:status "changed"})

  (delete cfg :builds "test-1")

  (query {:prefix "api"
          :ns "default"
          :apiVersion "v1"}
         :pods
         "aitem-hook-test-d40a2375646990b2dec75e80cf97ce5a8a77a199/log")

  (curl {} "api/v1/namespaces/default/pods/aitem-hook-test-d40a2375646990b2dec75e80cf97ce5a8a77a199/log")

  (create cfg :builds
          {:kind "Build"
           :apiVersion "ci3.io/v1"
           :metadata {:name "test-00"}})
  (-> @(http-client/get
        (url cfg (str "api/v1/namespaces/default/pods"))
        {:insecure? true
         :headers (merge default-headers {"Content-Type" "application/json"})})
      :body
      (json/parse-string))
  )

#_(query {:apiVersion "zeroci.io/v1"
        :ns "default"}
       :builds "test-1")

#_(query {:apiVersion "zeroci.io/v1"
        :ns "default"}
       :builds "test-1")
