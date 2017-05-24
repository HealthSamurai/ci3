(ns ci3.server
  (:require
   [clojure.string :as str]
   [org.httpkit.server :as http-kit]
   [org.httpkit.client :as http-client]
   [clojure.tools.logging :as log]
   [clojure.java.shell :as sh]
   [cheshire.core :as json]
   [pandect.algo.sha1 :refer [sha1-hmac]]
   [route-map.core :as route-map]
   [ring.util.codec])
  (:gen-class))

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
      {:body payload}
      {:status 401 })))


(def webhook
  (-> verify))

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


(defn -main [& args] (restart))

(comment (restart))
