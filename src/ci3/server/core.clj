(ns ci3.server.core
  (:require
   [ci3.server.watch :as watch]
   [ci3.build.core :as build]
   [unifn.rest :as rest]
   [ci3.server.watch]
   [ci3.repo.core :as repo]
   [unifn.formats]
   [unifn.core :as u]
   [unifn.env :as e]
   [hiccup.core :as hiccup]
   [hiccup.page :as page]
   [garden.core :as css]
   [clojure.walk :as walk]
   [ci3.k8s :as k8s]
   [ci3.telegram :as telegram]))

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

(defmethod u/*fn ::routes
  [{{routes :routes} :cache}]
  {:response {:body (var-get routes)}})

(defmethod u/*fn ::index
  [arg]
  {:response {:body (layout [:a {:href "/builds"} "builds"])}})

(defmethod u/*fn ::watches [_]
  {:response {:status 200
              :body (with-out-str (clojure.pprint/pprint  @watch/state))}})

(defmethod u/*fn
  ::configure
  [arg]
  {:k8s k8s/cfg})

(defmethod u/*fn
  ::webhook-verify
  [{req :request}]
  {:response {:status 200 :body "Ok! Wait for hook"}})


(defmethod u/*fn
  ::builds
  [{req :request}]
  (let [bs (walk/keywordize-keys (k8s/list k8s/cfg :builds))]
    {:response
     {:status 200
      :body
      (layout
         [:div.container
          (for [b (reverse (sort-by (fn [x] (get-in x [:payload :commit :timestamp])) (:items bs)))]
              [:div {:key (get-in b [:metadata :name])}
               [:a {   :href (str "/builds/" (get-in b [:metadata :name]))}
                [:b (get-in b [:payload :commit :timestamp])]
                " by "
                [:span (get-in b [:commit :author :name])]
                " - "
                [:span (get-in b [:commit :message])]]
               [:div "pod: "   (get-in b [:metadata :name])]
               [:hr]])])
      :headers {"Content-Type" "text/html"}}}))

(defmethod u/*fn
  ::logs
  [{{{id :id} :route-params} :request}]
  (let [logs (k8s/curl {} (str  "api/v1/namespaces/default/pods/build-" id "/log"))]
    {:response
     {:status 200
      :body (layout
             [:div.container-fluid
              [:pre logs]])
      :headers {"Content-Type" "text/html"}}}))

(def routes
  {:GET ::index
   "repositories" {:GET ::repositories}
   "watches"      {:GET ::watches}
   "webhook" { [:id] {:POST ::repo/webhook
                      :GET  ::webhook-verify}}
   "builds" {:GET ::builds
             [:id] {:GET ::logs} }})


(def metadata
  {:cache {:routes #'routes}
   :watch {:timeout 5000
           :resources [{:handler ::repo/init
                        :apiVersion "ci3.io/v1"
                        :resource :repositories
                        :ns "default"}
                       {:handler ::build/build
                        :apiVersion "ci3.io/v1"
                        :resource :builds
                        :ns "default"}]}
   :web [:unifn.routing/dispatch
         :unifn.formats/response]
   :bootstrap [::e/env ::configure]
   :config {:web {:port 8888}}})


(defn exec [& args]
  (telegram/start)
  (rest/restart metadata)
  (u/*apply [::e/env :k8s/watch] metadata))

(comment
  (rest/restart metadata))
