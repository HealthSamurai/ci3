(ns ci3.github
  (:require
    [org.httpkit.client :as http-client]
    [clj-json-patch.core :as patch]
    [clojure.walk :as walk]
    [ci3.k8s :as k8s]
    [cheshire.core :as json]
    [cheshire.core :as json]))

(defn create-webhook [repo]
  (let [secret (or (k8s/secret "ci3" (keyword (or (:secret repo) "defaultSecret")))
                   "defaultSecret")
        gh-token (k8s/secret "secrets" :github_token) ]
    (-> @(http-client/post
           (str "https://api.github.com/repos/" (:fullName repo) "/hooks")
           {:body (json/generate-string{:name "web"
                                        :active true
                                        :events ["push"]
                                        :config {:url (str "https://ci.health-samurai.io/webhook/" (get-in repo [:metadata :name]))
                                                 :content_type "json"
                                                 :secret secret }})
            :headers  { "Authorization" (str "token " gh-token) }})
        :body
        (json/parse-string))))

(comment
  (create-webhook {:fullName "HealthSamurai/ci3" :secret "mySecret" :metadata {:name "ci3"}})
  )
