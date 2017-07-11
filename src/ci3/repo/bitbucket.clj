(ns ci3.repo.bitbucket
  (:require [cheshire.core :as json]
            [ci3.repo.interface :as interf]
            [clojure.tools.logging :as log]
            [org.httpkit.client :as http]
            [unifn.core :as u]
            [unifn.env :as e]
            [environ.core :as env]
            [ci3.fx]
            [ci3.k8s :as k8s]
            [clojure.spec.alpha :as s]
            [clojure.string :as str]))

(def auth-endpoint "https://bitbucket.org/site/oauth2/access_token")

(defmethod u/*fn
  :bitbucket/slug
  [{{url :url} :repository}]
  {:repository {:slug (->> (str/split url #"bitbucket.org/") second)}})

(defmethod u/*fn
  :bitbucket/hook-url
  [{{hooks-url :hooks-url} :env {{nm :name} :metadata} :repository}]
  {:hook-url (str "http://ci-cleo.healt-samurai.io/webhook/" nm)})

(defmethod u/*fn
  :bitbucket/not-initi alized?
  [{{{status :status} :webhook} :repository}]
  (when (= "installed" status)
    (println "Already installed")
    {::u/status :stop
     ::u/message "already installed"}))

(s/def :bitbucket/key string?)
(s/def :bitbucket/secret string?)
(s/def :bitbucket/oauthConsumer
  (s/keys :req-un [:bitbucket/key :bitbucket/secret]))

(s/def :bitbucket/repository
  (s/keys :req-un [:bitbucket/oauthConsumer]))

(s/def :bitbucket/access-token-request
  (s/keys :req-un [:bitbucket/repository]))


(defmethod u/*fn :bitbucket/access-token-request
  [{{{k :key s :secret} :oauthConsumer} :repository}]
  {:fx {:http {:method :post
               :url auth-endpoint
               :fx/result [:tokens]
               :form-params {:grant_type "client_credentials"}
               :format :json
               :basic-auth  (str k ":" s)}}})


(s/def ::access_token string?)
(s/def ::tokens (s/keys :req-un [::access_token]))
(s/def :bitbucket/get-hooks (s/keys :req-un [::tokens]))

(defmethod u/*fn
  :bitbucket/get-hooks
  [{{access-token :access_token} :tokens {slug :slug} :repository}]
  {:fx {:http {:method :get
               :url (str "https://api.bitbucket.org/2.0/repositories/" slug "/hooks")
               :fx/result [:hooks]
               :format :json
               :oauth-token access-token}}})

(defmethod u/*fn
  :bitbucket/hook-not-present
  [{hook-url :hook-url {hooks :values} :hooks}]
  (when (some #(= hook-url (get % :url)) hooks)
    {::u/status :stop
     ::u/message "Hook already present"}))

(defmethod u/*fn
  :bitbucket/add-hook
  [{hook-url :hook-url
   {access-token :access_token} :tokens
   {slug :slug {name :name} :metadata} :repository}]
  {:fx {:http {:oauth-token access-token
               :url (str "https://api.bitbucket.org/2.0/repositories/" slug "/hooks")
               :method :post
               :format :json
               :fx/result [:hook]
               :body {:description "ci3 webhook"
                      :url hook-url
                      :active true
                      :events ["repo:push"]}}}})

(defmethod u/*fn
  :bitbucket/update-repo
  [{hook :hook {{nm :name v :resourceVersion } :metadata} :repository}]
  {:fx {:k8s/patch {:resource :repositories
                    :config {:apiVersion "ci3.io/v1" :ns "default"}
                    :id nm
                    :data {:webhook {:status "installed"
                                     :hook (:body hook)
                                     :version v
                                     :ts (java.util.Date.)}}}}})

(defmethod u/*fn
  :bitbucket/ensure-hook
  [arg]
  (u/*apply
   [:bitbucket/not-initialized?
    :bitbucket/slug
    :bitbucket/hook-url
    :bitbucket/access-token-request
    :bitbucket/get-hooks
    :bitbucket/hook-not-present
    :bitbucket/add-hook]
   arg)) 
(defmethod interf/init
  :bitbucket
  [env repo]
  (u/*apply [:bitbucket/ensure-hook
             :bitbucket/update-repo]
            {:env env :repository repo}))

(comment
  (u/*apply
   [::e/env
    :bitbucket/get-hooks]
   {:repository  {:metadata {:name "ci3"}
                  :url "https://bitbucket.org/healthsamurai/ci3"
                  :oauthConsumer {:key (env/env :bitbucket-key)
                                  :secret (env/env :bitbucket-secret)}}})
  (u/*apply
   [::e/env
    :bitbucket/ensure-hook]
   {:repository  (k8s/resolve-secrets (k8s/find k8s/cfg :repositories "ci3"))})

  (k8s/find k8s/cfg :repositories "ci3")
  (k8s/find k8s/cfg :repositories "ci3")

  (u/*apply
   [::e/env
    :bitbucket/ensure-hook
    :bitbucket/update-repo]
   {:repository  (k8s/resolve-secrets (k8s/find k8s/cfg :repositories "ci3"))})

  (k8s/find k8s/cfg :repositories "ci3")

  )
