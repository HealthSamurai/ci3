(ns ci3.repo.core
  (:require [ci3.k8s :as k8s]
            [ci3.repo.interface :as interf]
            [ci3.repo.bitbucket]
            [ci3.repo.github]
            [ci3.env :as env]
            [clojure.tools.logging :as log]))

(defn watch []
  (let [e (env/environment)
        repos (k8s/list k8s/cfg :repositories)]
    (doseq [repo (get repos "items")]
      (interf/init e repo))))

;; (remove-all-methods interf/init)


(comment

  (watch)

  (get-in (k8s/list k8s/cfg :repositories) ["items" 1 "oauthConsumer"])


  )
