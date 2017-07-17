(ns ci3.server
  (:require
   [clojure.string :as str]
   [org.httpkit.server :as http-kit]
   [org.httpkit.client :as http-client]
   [clojure.tools.logging :as log]
   [clojure.java.shell :as sh]
   [cheshire.core :as json]
   [ci3.k8s :as k8s]
   [ci3.telegram :as telegram]
   [ci3.controller :as ctrl]
   [pandect.algo.sha1 :refer [sha1-hmac]]
   [route-map.core :as route-map]
   [ring.util.codec]
   [hiccup.core :as hiccup]
   [hiccup.page :as page]
   [garden.core :as css]
   [clojure.walk :as walk]))

(defonce server (atom nil))

(def cfg {:apiVersion "ci3.io/v1" :ns "default"})
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
  [{ {id :id} :route-params headers :headers body :body :as req}]
  (let [signature (get headers "x-hub-signature")
        payload (slurp body)
        repo (k8s/find cfg :repositories id)
        secret (k8s/secret "ci3" (keyword (or (get repo "secret") "defaultSecret")))
        hash (str "sha1=" (sha1-hmac payload secret)) ]
    (if (and
          (not (= (get repo "code") 404))
          (= signature hash))
      (json/parse-string payload keyword)
      nil)))

(defn cleanup [s]
  (-> s
      (str/replace  #"\/" "-")
      (str/replace  #"\_" "-")
      (str/replace  #"\:" "-")
      (str/lower-case)))


(def longstring "healthsamurai-yourwaitsapp-d6b1fa98d56db635825fc2d4a4c1ea72801931e6")

(defn create-build [payload]
  (let [repository (:repository payload)
        commit (last (:commits payload))
        hashcommit (:id commit)
        build-name (cleanup (str (:full_name repository) "-" hashcommit))
        build-name (if (> (count build-name) 63) (subs build-name 0 63) build-name)]
    {:body (k8s/create cfg :builds
                       {:kind "Build"
                        :apiVersion "ci3.io/v1"
                        :metadata {:name  build-name}
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
  (if-let [payload (verify req)]
    (create-build payload)
    {:status 401 :body "401 Unauthorized"}))

(defn layout [cnt]
  (page/html5
   [:head
    [:meta {:charset "utf-8"}]
    [:link {:rel "stylesheet" :href "https://maxcdn.bootstrapcdn.com/bootstrap/4.0.0-alpha.6/css/bootstrap.min.css"}]
    [:link {:rel "stylesheet" :href     "https://maxcdn.bootstrapcdn.com/font-awesome/4.7.0/css/font-awesome.min.css"}]]
   [:body
    [:style (css/css [:body {:padding "0 20px"} [:.top {:padding "5px 10px" :background-color "#eee" :margin-bottom "10px"}]])]
    [:div.container-fluid.top "ci3"]
    cnt]))

(defn welcome [_]
  {:body (layout [:h3 "Welocome to minimalistic ci on k8s - ci3"])
   :headers {"Content-Type" "text/html"}})

(defn builds [_]
  (let [bs (walk/keywordize-keys (k8s/list k8s/cfg :builds))]
    {:body (layout
            [:div.container
             (for [b (reverse (sort-by (fn [x] (get-in x [:payload :commit :timestamp])) (:items bs)))]
               [:div {:key (get-in b [:metadata :name])}
                [:a {   :href (str "/builds/" (get-in b [:metadata :name]))}
                 [:b (get-in b [:payload :commit :timestamp])]
                 " by "
                 [:span (get-in b [:payload :commit :author :name])]
                 " - "
                 [:span (get-in b [:payload :commit :message])]]
                [:div "pod:"   (get-in b [:pod :metadata :name])]
                [:hr]])])
     :headers {"Content-Type" "text/html"}}))

(defn logs [{{id :id} :route-params}]
  (let [logs (k8s/curl {} (str  "api/v1/namespaces/default/pods/" id "/log"))]
    {:body (layout
            [:div.container-fluid
             [:pre logs]])
     :headers {"Content-Type" "text/html"}}))

(def routes
  {:GET #'welcome
   "builds" {:GET #'builds
             [:id] {:GET #'logs} }
   "webhook" { [:id] {:POST #'webhook}}})

(defn app [{meth :request-method uri :uri :as req}]
  (if-let [res (route-map/match [meth uri] routes)]
    ((:match res)  (assoc req :route-params (:params res)))
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
  (ctrl/watch)
  (telegram/start)
  (restart))

(comment
  (restart))
