(ns ci3.repo.core-test
  (:require [ci3.repo.core :as sut]
            [matcho.core :refer [match]]
            [ci3.k8s :as k8s]
            [unifn.core :as u]
            [clojure.test :refer :all]))

(def rid "test-repo")
(deftest repo-core

  (k8s/create k8s/cfg :repositories
              {:apiVersion "ci3.io/v1"
               :kind "Repository"
               :metadata {:name rid}
               :type "bitbucket"
               :fullName "HealthSamurai-test/test-repo"
               :url "https://bitbucket.org/healthsamurai-test/test-repo"})
  (match
   (u/*apply
    ::sut/webhook
    {:request {:route-params {:id "some-fake-id"}}})
   {:response {:status 400
               :body "Hook not found"}})

  (match
   (u/*apply
    ::sut/webhook
    {:request {:route-params {:id rid}}})
   {:response {:status 400
               :body "Invalid payload"}})
  (match
   (u/*apply
    ::sut/webhook
    {:request {:body "{\"repository\": {\"fullName\": \"healthsamurai-test/test-repo\"}}"
               :route-params {:id rid}}})
   {:response {:status 200}})

  (k8s/delete k8s/cfg :repositories "test-repo")

  )

