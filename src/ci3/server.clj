(ns ci3.server
  (:require
   [clojure.string :as str]
   [org.httpkit.server :as http-kit]
   [org.httpkit.client :as http-client]
   [clojure.tools.logging :as log]
   [clojure.java.shell :as sh]
   [cheshire.core :as json]
   [ci3.k8s :as k8s]
   [pandect.algo.sha1 :refer [sha1-hmac]]
   [route-map.core :as route-map]
   [ring.util.codec]))

(defonce server (atom nil))

(defn exec [& args]
  (log/info "Execute: " args)
  (let [res (apply sh/sh args)]
    (log/info "Result: " res)
    res))

(defn process-keys [res]
  (reduce (fn [acc [k v]]
            (let [pth (mapv keyword (str/split k #"\."))]
              (assoc-in acc pth v)))
          {} (get res "data")))

(defn repo-key [h]
  (fn [req]
    (let [repo-key (str/replace (:uri req) #"^/" "")]
      (if (str/blank? repo-key)
        {:body (str "Hi, i'm zeroci please use /repo-key path to trigger")}
        (h (assoc req :repo-key repo-key :log []))))))

(defn get-repositories [h]
  (fn [req]
    (if-let [rs (let [res (exec "kubectl" "get" "configmaps" "repositories" "-o" "json")]
                  (log/info "Repositories" res)
                  (process-keys (json/parse-string (:out res))))]
      (h (assoc req :repositories rs))
      {:body (str "Could not get repositories configmap") :status 500})))

(defn get-config [h]
  (fn [{rk :repo-key rs :repositories :as req}]
    (if-let [cfg (get rs (keyword rk))]
      (h (assoc req :config cfg))
      {:body (str "Could not get config for " rk) :status 500})))

(defn verify
  [{headers :headers body :body :as req}]
  (let [signature (get headers "x-hub-signature")
        payload (slurp body)
        hash (str "sha1=" (sha1-hmac payload "secret")) ]
    (if (= signature hash)
      (json/parse-string payload keyword)
      {:status 401 })))


(def cfg {:apiVersion "ci3.io/v1" :ns "default"})


(defn cleanup [s]
  (-> s
      (str/replace  #"\/" "-")
      (str/replace  #"\_" "-")
      (str/replace  #"\:" "-")))

(defn create-build [payload]
  (let [repository (:repository payload)
        commit (last (:commits payload))
        hashcommit (:id commit)
        build-name (cleanup (str (:full_name repository) "-" hashcommit)) ]
    {:body (k8s/create cfg :builds
                       {:kind "Build"
                        :apiVersion "ci3.io/v1"
                        :metadata {:name  hashcommit}
                        :payload {:ref (:ref payload)
                                  :diff (:compare payload)
                                  :repository (select-keys repository
                                                           [:name :organization :full_name
                                                            :url :html_url :git_url :ssh_url
                                                            ]
                                                           )
                                  :commit (select-keys commit
                                                       [:id :message :timestamp
                                                        :url :author ])
                                 } })}))

(defn webhook [req]
  (-> req
      verify
      create-build
      ))

(defn welcome [_]
  {:body "Welocome to zeroci"})

(defn builds [_]
  (let [url "http://localhost:8001/api/v1/namespaces/default/pods?labelSelector=system=ci"
        url "http://localhost:8001/apis/zeroci.io/v1/builds"
        res (http-client/get url)]
    {:body (:body @res)}))

(def routes
  {:GET #'welcome
   "builds" {:GET #'builds}
   "webhook"  {:POST #'webhook}})


(defn app [{meth :request-method uri :uri :as req}]
  (if-let [res (route-map/match [meth uri] routes)]
    ((:match res)  (assoc req :route-params req))
    {:status 404 :body (str meth " " uri " Not found")}))

(defn restart []
  ;; todo validate config
  (when-let [s @server]
    (log/info "Stoping server")
    (@server)
    (reset! server nil))
  (log/info "Starting server on " 8888)
  (reset! server (http-kit/run-server #'app {:port 8888})))


(defn exec [& args]
  (restart))

(comment
  (restart)
  )
