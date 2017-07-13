(ns ci3.agent.core-test
  (:require [ci3.agent.core :as sut]
            [matcho.core :refer [match]]
            [ci3.repo.core-test :as repo]
            [ci3.k8s :as k8s]
            [unifn.core :as u]
            [clojure.test :refer :all]))

(def bid "test-build")
(def hashcommit "2242735bf839c149c48d4d73f3af434bb0bf0806")

(defn build-fixture [f]
  (k8s/create k8s/cfg :builds
              {:apiVersion "ci3.io/v1"
               :kind "Build"
               :metadata {:name bid}
               :hashcommit hashcommit
               :repository repo/rid})
  (f)
  (k8s/delete k8s/cfg :builds bid))

(use-fixtures :once build-fixture repo/repo-fixture )

(deftest agent-checkout
  (match
   (sut/run {:env {:build-id bid}})
   {::sut/build      {:metadata {:name bid}}
    ::sut/repository {:metadata {:name repo/rid}}
    ::sut/checkout   hashcommit
    }

   ))
