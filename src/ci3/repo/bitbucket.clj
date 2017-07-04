(ns ci3.repo.bitbucket
  (:require [cheshire.core :as json]
            [ci3.repo.interface :as interf]
            [clojure.tools.logging :as log]
            [org.httpkit.client :as http]
            [ci3.env :as env]
            [unifn.core :as u]
            [clojure.string :as str]
            [clojure.spec.alpha :as s]))

;; https://confluence.atlassian.com/bitbucket/oauth-on-bitbucket-cloud-238027431.html#OAuthonBitbucketCloud-Step4.RequestanAccessToken

(def auth-endpoint "https://bitbucket.org/site/oauth2/access_token")

(s/def :bitbucket/key string?)
(s/def :bitbucket/secret string?)
(s/def :bitbucket/oauthConsumer
  (s/keys :req-un [:bitbucket/key :bitbucket/secret]))

(s/def :bitbucket/repository
  (s/keys :req-un [:bitbucket/oauthConsumer]))

(s/def :bitbucket/access-token-request
  (s/keys :req-un [:bitbucket/repository]))

(defmethod u/*fn
  :http
  [args]
  (println args)
  (let [res @(http/request args)]
    (if (< (:status res) 400)
      (update res :body (fn [x] (when x (json/parse-string x keyword))))
      {::u/status :error
       ::u/errors res
       ::u/message (str "Request failed with")})))


(defmethod u/*fn
  :bitbucket/access-token-request
  [{{{k :key s :secret} :oauthConsumer} :repository}]
  {:fx {:http {:method :post
               :url auth-endpoint
               :fx/result [:bitbucket/access-token-response]
               :form-params {:grant_type "client_credentials"}
               :format :json
               :basic-auth  (str k ":" s)}}})


(defmethod u/*fn
  :bitbucket/get-hooks
  [{access-token :access-token {slug :slug} :repository}]
  {:fx {:http {:method :get
               :url (str "https://api.bitbucket.org/2.0/repositories/" slug "/hooks")
               :result [:bitbucket/get-hooks]
               :format :json
               :oauth-token access-token}}})

(defmethod u/*fn
  :bitbucket/add-hook
  [{{hooks-url :hooks-url} :env access-token :access-token {slug :slug} :repository}]
  {:fx {:http
        {:oauth-token access-token
         :url (str "https://api.bitbucket.org/2.0/repositories/" slug "/hooks")
         :format :json
         :result [:bitbucket/hook]
         :body {:description "ci3 webhook"
                :url hooks-url
                :active true
                :events ["repo:push"]}}}})



(defn get-access-token [api-key api-secret]
  (let [code-req @(http/post auth-endpoint
                             {:basic-auth (str api-key ":" api-secret)
                              :form-params {:grant_type "client_credentials"}})]
    (log/info "Code request " (:status code-req))
    (-> code-req
        :body
        (json/parse-string keyword)
        :access_token)))


(defn get-hooks [env repo-slug access-token]
  (-> @(http/get (str "https://api.bitbucket.org/2.0/repositories/" repo-slug "/hooks")
                 {:oauth-token access-token})
      :body
      (json/parse-string keyword)
      :values))

(defn add-hook [env repo-slug access-token]
  (let [resp @(http/post (str "https://api.bitbucket.org/2.0/repositories/" repo-slug "/hooks")
                         {:oauth-token access-token
                          :body (json/generate-string
                                 {:description "ci3 webhook"
                                  :url  (:hooks-url env)
                                  :active true
                                  :events ["repo:push"]})})]
    (log/info "Add hook:" resp)
    resp))

(defn ensure-hook [env repo-slug access-token]
  (let [hooks (get-hooks env repo-slug access-token)
        hooks-url (:hooks-url env)]
    (if-not (some #(= hooks-url (get % :url)) hooks)
      (add-hook env repo-slug access-token)
      (log/info repo-slug " already has ci3 hook"))))

(defmethod interf/init
  :bitbucket
  [env repo]
  (log/info "INIT bitbucket repo" repo)
  (let [{k "key" s "secret"} (get repo "oauthConsumer")
        access-token (get-access-token (str/trim k) (str/trim s))
        repo-slug (->> (str/split (get repo "url") #"bitbucket.org/") second)]
    (log/info "GET token "  access-token " by " k " and " s)
    (ensure-hook env repo-slug access-token)))

(comment

  (def env {:hooks-url "http://cleo-ci.health-samurai.io/webhook"})

  (def tokens 
    (get-access-token (env/env :BITBUCKET_KEY)
                      (env/env :BITBUCKET_SECRET)))

  tokens

  (mapv :url (get-hooks {} "cleoemr/ema-itegration" tokens))

  (-> @(http/get "https://api.bitbucket.org/2.0/repositories/cleoemr/ema-itegration/hooks" {:oauth-token tokens})
      :body
      (json/parse-string keyword))


  )
