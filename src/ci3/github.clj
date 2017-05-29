(ns ci3.github
  (:require
    [org.httpkit.client :as http-client]
    [clj-json-patch.core :as patch]
    [clojure.walk :as walk]
    [ci3.k8s :as k8s]
    [cheshire.core :as json]
    [cheshire.core :as json]))

(def gh-token (k8s/secret "secrets" :github_token))

(defn create-webhook [repo]
  (let [secret (or (k8s/secret "ci3" (keyword (or (:secret repo) "defaultSecret"))) "defaultSecret") ]
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


(defn set-status [build]
  (let [sha (get-in build [:payload :commit :id])
        full_name (get-in build [:payload :repository :full_name])
        target_url (str "https://ci.health-samurai.io/builds/" (get-in build [:metadata :name]))
        status (-> @(http-client/post
                      (str "https://api.github.com/repos/" full_name "/statuses/" sha)
                      {:body (json/generate-string{:context "continuous-integration/ci3"
                                                   :description "success"
                                                   :target_url target_url
                                                   :state "success" })
                       :headers  { "Authorization" (str "token " gh-token) }})
                   :body
                   (json/parse-string))]
    (println status)
    status))

(comment
  (create-webhook {:fullName "HealthSamurai/ci3" :secret "mySecret" :metadata {:name "ci3"}})

  (set-status {:kind "Build"
               :metadata {:name "healthsamurai-ci3-4aa99cc52848f9cbc67286bb876c7287b27c717d"}
               :payload {:commit {:id "fc45e0b88a00d3de6a040db107b2a5b6c0c2bc03"}
                         :repository {:full_name "HealthSamurai/ci3"} }} )
  )
