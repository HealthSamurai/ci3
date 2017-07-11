(ns ci3.repo.core
  (:require [ci3.k8s :as k8s]
            [ci3.repo.interface :as interf]
            [ci3.repo.bitbucket :as bitbucket]
            [ci3.repo.github]
            [clojure.tools.logging :as log]
            [unifn.core :as u]))

(defmethod u/*fn
  ::repository
  [{env :env res :resource :as arg}]
  (println "Register webhook" res)
  (interf/init env res))

(comment
  (watch)
  (get-in (k8s/list k8s/cfg :repositories) ["items" 1 "oauthConsumer"]))
