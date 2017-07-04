(ns ci3.repo.github
  (:require [ci3.repo.interface :as interf]
            [ci3.k8s :as k8s]
            [cheshire.core :as json]
            [org.httpkit.client :as http]
            [clojure.tools.logging :as log]))

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

