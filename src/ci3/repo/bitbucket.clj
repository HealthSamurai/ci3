(ns ci3.repo.bitbucket
  (:require [cheshire.core :as json]
            [ci3.repo.interface :as interf]
            [clojure.tools.logging :as log]
            [org.httpkit.client :as http]
            [clojure.java.shell :as sh]
            [unifn.core :as u]
            [unifn.env :as e]
            [clojure.walk]
            [environ.core :as env]
            [ci3.fx]
            [ci3.k8s :as k8s]
            [clojure.spec.alpha :as s]
            [clojure.string :as str]))

(def auth-endpoint "https://bitbucket.org/site/oauth2/access_token")
(def bb-api        "https://api.bitbucket.org/2.0/repositories/")

(s/def ::hook-url (s/keys :req-un [::env]))
(s/def ::env      (s/keys :req-un [::hostname]))
(s/def ::hostname string?)
(s/def ::key string?)
(s/def ::secret string?)
(s/def ::oauthConsumer (s/keys :req-un [::key ::secret]))
(s/def ::repository (s/keys :req-un [::oauthConsumer]))
(s/def ::access-token-request (s/keys :req-un [::repository]))
(s/def ::tokens (s/keys :req-un [::access_token]))
(s/def ::get-hooks (s/keys :req-un [::tokens]))

(defmethod u/*fn
  ::slug
  [{{url :url} :repository}]
  {:repository {:slug (->> (str/split url #"bitbucket.org/") second)}})

(defmethod u/*fn
  ::hook-url
  [{{host :hostname} :env {{nm :name} :metadata} :repository}]
  {:hook-url (str host "/webhook/" (name nm))})

(defmethod u/*fn
  ::not-initialized?
  [{{{status :status} :webhook} :repository}]
  (when (= "installed" status)
    (log/info "Already installed")
    {::u/status :stop
     ::u/message "Already installed"}))

(defmethod u/*fn
  ::access-token-request
  [{{{k :key s :secret} :oauthConsumer} :repository}]
  {:fx {:http {:method :post
               :url auth-endpoint
               :fx/result [:tokens]
               :form-params {:grant_type "client_credentials"}
               :format :json
               :basic-auth  (str k ":" s)}}})

(defmethod u/*fn
  ::get-hooks
  [{{access-token :access_token} :tokens {slug :slug} :repository}]
  {:fx {:http {:methoed :get
               :url (str bb-api slug "/hooks")
               :fx/result [:hooks]
               :format :json
               :oauth-token access-token}}})

(defmethod u/*fn
  ::hook-not-present
  [{hook-url :hook-url {hooks :values} :hooks}]
  (when (some #(= hook-url (get % :url)) hooks)
    {::u/status :stop
     ::u/message "Hook already present"}))

(defmethod u/*fn
  ::add-hook
  [{hook-url :hook-url
   {access-token :access_token} :tokens
   {slug :slug {name :name} :metadata} :repository}]
  (log/info "Add hook")
  {:fx {:http {:oauth-token access-token
               :url (str bb-api slug "/hooks")
               :method :post
               :format :json
               :fx/result [:hook]
               :body {:description "ci3 webhook"
                      :url hook-url
                      :active true
                      :events ["repo:push"]}}}})

(defmethod u/*fn
  ::update-repo
  [{hook :hook {{nm :name v :resourceVersion } :metadata} :repository}]
  {:fx {:k8s/patch {:resource :repositories
                    :config {:apiVersion "ci3.io/v1" :ns "default"}
                    :id nm
                    :data {:webhook {:status "installed"
                                     :hook (:body hook)
                                     :version v
                                     :ts (java.util.Date.)}}}}})

(defmethod u/*fn
  ::ensure-hook
  [arg]
  (u/*apply
   [::k8s/resolve-secrets
    ::not-initialized?
    ::slug
    ::hook-url
    ::access-token-request
    ::get-hooks
    ::hook-not-present
    ::add-hook]
   arg))

(defmethod interf/init
  :bitbucket
  [env repo]
  (u/*apply
   [::ensure-hook
    ::update-repo]
   {:env env :repository repo}))

(defmethod u/*fn
  ::mk-build-resource
  [{{payload :body :as req} :request
    build-name :ci3.repo.core/build-name
    repository  :ci3.repo.core/repository}]
  {:ci3.repo.core/build
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
                            :url :author ]) })})

(defmethod u/*fn
  ::verify
  [{{ip :remote-addr body :body} :request repo :ci3.repo.core/repository}]
  ;; verify ip should be one of
  ;; 104.192.143.0/24 34.198.203.127 34.198.178.64 34.198.32.85
  (let [payload (json/parse-string body keyword )]
    (if-not (= (str/lower-case (:fullName repo))
               (str/lower-case (or (get-in payload [:repository :full_name]) "")))
      {::u/status :error
       ::u/message (str "Invalid payload")})))

(defmethod interf/mk-build
  :bitbucket
  [arg]
  (u/*apply
   [::verify
    ::mk-build-resource]
   arg))


(defmethod interf/checkout-project
  :bitbucket
  [{env :env workspace :ci3.agent.core/workspace
    build :ci3.agent.core/build repo :ci3.repo.core/repository}]
  (log/info "Clone repo" (:url repo) )
  (sh/sh "rm" "-rf" workspace)
  (let [{err :err exit :exit :as res} (sh/sh "git" "clone" (:url repo) workspace)]
    (if (= 0 exit)
      (let [{err :err exit :exit :as res}
            (sh/sh "git" "reset" "--hard" (:hashcommit build) :dir workspace)]
        (if-not (= 0 exit)
          {::u/status :error
           ::u/message err}
          {:checkout (:hashcommit build)}))
      {::u/status :error
       ::u/message err})))

(comment
  (u/*apply
   [::get-hooks]
   {:repository  {:metadata {:name "ci3"}
                  :url "https://bitbucket.org/healthsamurai/ci3"
                  :oauthConsumer {:key (env/env :bitbucket-key)
                                  :secret (env/env :bitbucket-secret)}}})
  (u/*apply
   [::e/env
    ::ensure-hook]
   {:repository  (k8s/resolve-secrets (k8s/find k8s/cfg :repositories "ci3"))})

  (k8s/find k8s/cfg :repositories "ci3")
  (k8s/resolve-secrets (k8s/find k8s/cfg :repositories "ci3")))
