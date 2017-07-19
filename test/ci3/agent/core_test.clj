(ns ci3.agent.core-test
  (:require [ci3.agent.core :as sut]
            [ci3.repo.core]
            [ci3.repo.github]
            [ci3.repo.bitbucket]
            [matcho.core :refer [match]]
            [ci3.k8s :as k8s]
            [unifn.core :as u]
            [clojure.test :refer :all]))

(def bid "build-112233")
(def bid-src "build-223344")
(def rid "ci3public")
(def rid-src "ci3public-src")
(def hashcommit "0b9429c4b4f520457c3b80347d1bca4b3d79c1c6")
(def cfg {:apiVersion "ci3.io/v1" :ns "test"})

(defn agent-fixture [f]
  (k8s/create cfg :builds
              {:apiVersion "ci3.io/v1"
               :kind "Build"
               :metadata {:name bid}
               :hashcommit hashcommit
               :repository rid})
  (k8s/create cfg :repositories
              {:apiVersion "ci3.io/v1"
               :kind "Repository"
               :metadata {:name rid}
               :type "bitbucket"
               :fullName "Aitem/ci3-public"
               :url "https://bitbucket.org/Aitem/ci3-public"})

  (k8s/create cfg :builds
              {:apiVersion "ci3.io/v1"
               :kind "Build"
               :metadata {:name bid-src}
               :hashcommit hashcommit
               :repository rid-src})
  (k8s/create cfg :repositories
              {:apiVersion "ci3.io/v1"
               :kind "Repository"
               :metadata {:name rid-src}
               :root "src"
               :type "bitbucket"
               :fullName "Aitem/ci3-public"
               :url "https://bitbucket.org/Aitem/ci3-public"})

  (f)
  (k8s/delete cfg :builds bid)
  (k8s/delete cfg :repositories rid)
  (k8s/delete cfg :builds bid-src)
  (k8s/delete cfg :repositories rid-src)
  )

(use-fixtures :once agent-fixture)

(deftest agent-get-build
  (match
   (sut/run {:k8s cfg
             :env {:build-id bid :BUILD_ID bid}})
   {::sut/build  {:metadata {:name bid}
                  :repository rid}
    ::ci3.repo.core/repository {:metadata {:name rid}}
    :checkout   hashcommit
    ::sut/build-config {:description "build in root"}})

  (match
   (sut/run {:k8s cfg
             :env {:build-id bid-src :BUILD_ID bid-src}})
   {::sut/build  {:metadata {:name bid-src}
                  :repository rid-src}
    ::ci3.repo.core/repository {:metadata {:name rid-src}}
    :checkout   hashcommit
    ::sut/build-config {:description "build in src"}})

  )



#_(deftest agent-checkout
  (match
   (sut/run {:env {:build-id bid}})
   {::sut/build      {:metadata {:name bid}}
    ::sut/repository {:metadata {:name repo/rid}}
    ::sut/checkout   hashcommit
    ::sut/build-config {:kind "Build" }}
   ))
