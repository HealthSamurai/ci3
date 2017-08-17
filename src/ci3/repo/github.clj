(ns ci3.repo.github
  (:require [cheshire.core :as json]
            [ci3.repo.interface :as interf]
            [clojure.tools.logging :as log]
            [org.httpkit.client :as http]
            [pandect.algo.sha1 :refer [sha1-hmac]]
            [clojure.java.shell :as sh]
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
  (println "TODO: Github verify"))

;; TODO
(defn verify
  [{ {id :id} :route-params headers :headers body :body :as req}]
  (let [signature (get headers "x-hub-signature")
        payload (slurp body)
        repo (k8s/find k8s/cfg :repositories id)
        secret (k8s/secret "ci3" (keyword (or (get repo "secret") "defaultSecret")))
        hash (str "sha1=" (sha1-hmac payload secret)) ]
    (if (and
         (not (= (get repo "code") 404))
         (= signature hash))
      (json/parse-string payload keyword)
      nil)))


(defmethod u/*fn
  ::mk-build-resource
  [{{payload :body :as req} :request
    build-name :ci3.repo.core/build-name
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

(defmethod interf/checkout-project
  :github
  [{env :env workspace :ci3.agent.core/workspace
    build :ci3.agent.core/build repo :ci3.repo.core/repository}]
  (log/info "Clone repo" (:url repo) )
  (sh/sh "rm" "-rf" workspace)
  (let [token (get-in repo [:oauthConsumer :token])
        full-name (:fullName repo)
        url (str "https://" token "@github.com/" full-name ".git")
        {err :err exit :exit :as res} (sh/sh "git" "clone" url workspace)]
    (if (= 0 exit)
      (let [{err :err exit :exit :as res}
            (sh/sh "git" "reset" "--hard" (:hashcommit build) :dir workspace)]
        (if-not (= 0 exit)
          {::u/status :error
           ::u/message err}
          {:checkout (:hashcommit build)}))
      {::u/status :error
       ::u/message err})))


(defn verify
  [{ {id :id} :route-params headers :headers body :body :as req}]
  (let [signature (get headers "x-hub-signature")
        payload (slurp body)
        repo (k8s/find k8s/cfg :repositories id)
        secret (k8s/secret "ci3" (keyword (or (get repo "secret") "defaultSecret")))
        hash (str "sha1=" (sha1-hmac payload secret)) ]
    (if (and
         (not (= (get repo "code") 404))
         (= signature hash))
      (json/parse-string payload keyword)
      nil)))


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

