(ns ci3.repo.github
  (:require [cheshire.core :as json]
            [ci3.repo.interface :as interf]
            [clojure.tools.logging :as log]
            [org.httpkit.client :as http]
            [unifn.core :as u]
            [unifn.env :as e]
            [clojure.walk]
            [environ.core :as env]
            [ci3.fx]
            [ci3.k8s :as k8s]
            [clojure.spec.alpha :as s]
            [clojure.string :as str]))

(defmethod u/*fn
  ::verify
  [arg]
  (println "Github verify"))

(defn build-resource [{{payload :body :as req} :request
                       build-name ::build-name
                       repository  :ci3.repo.core/repository}]
  (let [payload (json/parse-string payload keyword)
        commit (last (:commits payload))
        hashcommit (get-in payload [:push :changes 0 :commits 0 :hash])
        diff (get-in payload [:push :changes 0 :links :html :href])]
    {:kind "Build"
     :apiVersion "ci3.io/v1"
     :metadata {:name  build-name}
     :hashcommit hashcommit
     :repository (get-in repository [:metadata :name])
     :diff diff
     :commit (select-keys commit
                          [:id :message :timestamp
                           :url :author ]) }))

(defmethod u/*fn
  ::mk-build-resource
  [{{payload :body :as req} :request
    build-name ::build-name
    repository  :ci3.repo.core/repository}]
  {:ci3.repo.core/build
   (let [payload (json/parse-string payload keyword)
         commit (last (:commits payload))
         hashcommit (:id commit)
         diff (get-in payload [:push :changes 0 :links :html :href])]
     {:kind "Build"
      :apiVersion "ci3.io/v1"
      :metadata {:name  build-name}
      :hashcommit hashcommit
      :repository (get-in repository [:metadata :name])
      :diff diff
      :commit (select-keys commit
                           [:id :message :timestamp
                            :url :author ]) })})

(defmethod interf/mk-build
  :github
  [arg]
  (u/*apply
   [::verify
    ::mk-build-resource]
   arg))













(defn create-webhook [access-token repo]
  (let [secret (or (k8s/secret "ci3" (keyword (or (:secret repo) "defaultSecret"))) "defaultSecret") ]
    (-> @(http/post
          (str "https://api.github.com/repos/" (:fullName repo) "/hooks")
          {:body (json/generate-string{:name "web"
                                       :active true
                                       :events ["push"]
                                       :config {:url (str "https://ci.health-samurai.io/webhook/" (get-in repo [:metadata :name]))
                                                :content_type "json"
                                                :secret secret}})
           :headers  { "Authorization" (str "token " access-token)}})
        :body
        (json/parse-string))))

(defn set-status [access-token build]
  (let [sha (get-in build [:payload :commit :id])
        full_name (get-in build [:payload :repository :full_name])
        target_url (str "https://ci.health-samurai.io/builds/" (get-in build [:metadata :name]))
        status (-> @(http/post
                     (str "https://api.github.com/repos/" full_name "/statuses/" sha)
                     {:body (json/generate-string{:context "continuous-integration/ci3"
                                                  :description "success"
                                                  :target_url target_url
                                                  :state (:status build) })
                      :headers  { "Authorization" (str "token " access-token) }})
                   :body
                   (json/parse-string))]
    status))


(defmethod interf/init
  :github
  [env repo]
  (log/info "INIT github repo" repo))

