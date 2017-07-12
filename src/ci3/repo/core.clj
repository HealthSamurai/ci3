(ns ci3.repo.core
  (:require [ci3.k8s :as k8s]
            [ci3.repo.interface :as interf]
            [ci3.repo.bitbucket :as bitbucket]
            [ci3.repo.github]
            [unifn.core :as u]))

(defmethod u/*fn
  ::init
  [{env :env {repo :object tp :type} :resource}]
  (when (= tp "ADDED" )
    (println "Register webhook")
    (interf/init env repo)))

(comment
  (watch)
  (get-in (k8s/list k8s/cfg :repositories) ["items" 1 "oauthConsumer"]))
