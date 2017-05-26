(ns ci3.github
  (:require
    [org.httpkit.client :as http-client]
    [clj-json-patch.core :as patch]
    [clojure.walk :as walk]
    [cheshire.core :as json]
    [cheshire.core :as json]))



(defn create-webhook [repo]
  (-> @(http-client/post
        (str "https://api.github.com/repos/" (:full_name repo) "/hooks")
        {:body (json/generate-string{:name "web"
                                     :active true
                                     :events ["push"]
                                     :config {:url "https://ci.health-samurai.io/webhook"
                                              :content_type "json"
                                              :secret (:secret repo) }})
         :headers  { "Authorization" (str "token fooo")
                    }})
      :body
      (json/parse-string)))

(comment
  (create-webhook {:full_name "HealthSamurai/ci3" :secret "dd"})
  )
