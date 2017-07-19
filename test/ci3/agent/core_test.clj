(ns ci3.agent.core-test
  (:require
   [ci3.agent.core :as sut]
   [matcho.core :refer [match]]
   [unifn.core :as u]
   [ci3.k8s :as k8s]
   [clojure.test :refer :all]))

(def bid "build-112233")
(def bid-src "build-223344")
(def rid "ci3public")
(def rid-src "ci3public-src")

(def ghbid "build-github")
(def ghrid "ci3githubpublic")

(def hashcommit "d6d601b11a5f99d213b063fea702ecfeae03e888")
(def github-hashcommit "553e3564848da9d2621c1d28e2f1fda0fa9ce6d8")
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

  ;; github
  (k8s/create cfg :builds
              {:apiVersion "ci3.io/v1"
               :kind "Build"
               :metadata {:name ghbid}
               :hashcommit github-hashcommit
               :repository ghrid})
  (k8s/create cfg :repositories
              {:apiVersion "ci3.io/v1"
               :kind "Repository"
               :metadata {:name ghrid}
               :root "src"
               :type "github"
               :fullName "Aitem/ci3-github-public"
               :oauthConsumer
               {:token
                {:valueFrom {:secretKeyRef {:name "github" :key "token"}}}}
               :url "https://github.com/Aitem/ci3-github-public.git"})

  (f)
  (k8s/delete cfg :builds bid)
  (k8s/delete cfg :builds bid-src)
  (k8s/delete cfg :builds ghbid)
  (k8s/delete cfg :repositories rid)
  (k8s/delete cfg :repositories rid-src)
  (k8s/delete cfg :repositories ghrid)
  )

(use-fixtures :once agent-fixture)

(deftest agent-get-build
  (match
   (sut/run {:k8s cfg
             :env {:build-id bid :BUILD_ID bid}})
   {::sut/build  {:metadata {:name bid}
                  :repository rid}
    :ci3.repo.core/repository {:metadata {:name rid}}
    :checkout   hashcommit
    ::sut/build-config {:description "build in root"}})

  (match
   (sut/run {:k8s cfg
             :env {:build-id bid-src :BUILD_ID bid-src}})
   {::sut/build  {:metadata {:name bid-src}
                  :repository rid-src}
    :ci3.repo.core/repository {:metadata {:name rid-src}}
    :checkout   hashcommit
    ::sut/build-config {:description "build in src"}})

  (match
   (sut/run {:k8s cfg
             :env {:build-id ghbid :BUILD_ID ghbid}})
   {::sut/build  {:metadata {:name ghbid}
                  :repository ghrid}
    :ci3.repo.core/repository {:metadata {:name ghrid}}
    :checkout   github-hashcommit
    ::sut/build-config {:description "build in src"}})


  )


