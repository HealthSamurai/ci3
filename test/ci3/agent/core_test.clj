(ns ci3.agent.core-test
  (:require
   [ci3.agent.core :as sut]
   [matcho.core :refer [match]]
   [unifn.core :as u]
   [unifn.env :as e]
   [ci3.k8s :as k8s]
   [clojure.tools.logging :as log]
   [clojure.test :refer :all]))

(def bid "build-112233")
(def bid-private "build-11223344")
(def bid-src "build-223344")
(def rid "ci3public")
(def rid-src "ci3public-src")
(def rid-private "ci3private")

(def ghbid "build-github")
(def ghrid "ci3githubpublic")

(def hashcommit "d6d601b11a5f99d213b063fea702ecfeae03e888")
(def bb-private-hashcommit "44fed27862a92b2106572e2630491c432807cc73")
(def github-hashcommit "f9fdb8088dae6c41f847b34b4a0f1142312007a0")
(def cfg {:apiVersion "ci3.io/v1" :ns "test"})

(defn agent-fixture [f]
  (k8s/create cfg :builds
              {:apiVersion "ci3.io/v1"
               :kind "Build"
               :test true
               :metadata {:name bid}
               :hashcommit hashcommit
               :repository rid})
  (k8s/create cfg :repositories
              {:apiVersion "ci3.io/v1"
               :kind "Repository" :test true :metadata {:name rid}
               :type "bitbucket"
               :fullName "Aitem/ci3-public"
               :url "https://bitbucket.org/Aitem/ci3-public"})


  ;; BB private
  (k8s/create cfg :builds
              {:apiVersion "ci3.io/v1"
               :kind "Build"
               :test true
               :metadata {:name bid-private}
               :hashcommit bb-private-hashcommit
               :repository rid-private})
  (k8s/create cfg :repositories
              {:apiVersion "ci3.io/v1"
               :kind "Repository"
               :test true
               :metadata {:name rid-private}
               :type "bitbucket"
               :fullName "Aitem/private-test"
               :oauthConsumer
               {:token
                {:valueFrom {:secretKeyRef {:name "bitbucket" :key "token"}}}}
               :url "https://bitbucket.org/Aitem/private-test"})


  (k8s/create cfg :builds
              {:apiVersion "ci3.io/v1"
               :kind "Build"
               :test true
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
               :oauthConsumer
               {:token
                {:valueFrom {:secretKeyRef {:name "bitbucket" :key "token"}}}}
               :url "https://bitbucket.org/Aitem/ci3-public"})

  ;; github
  (k8s/create cfg :builds
              {:apiVersion "ci3.io/v1"
               :kind "Build"
               :test true
               :branch "master"
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
  (k8s/delete cfg :builds bid-private)
  (k8s/delete cfg :builds ghbid)
  (k8s/delete cfg :repositories rid)
  (k8s/delete cfg :repositories rid-private)
  (k8s/delete cfg :repositories rid-src)
  (k8s/delete cfg :repositories ghrid))

(use-fixtures :once agent-fixture)


(defn run [& [arg]]
  (log/info "Run agent")
  (u/*apply
   [::sut/get-build
    ::sut/get-repository
    ::sut/workspace
    ::sut/checkout-project
    ::sut/get-build-config
    ::sut/run-build
    ::sut/catch-errors {::u/intercept :all}]
   arg))


(defn arg [a]
  (merge-with merge (u/*apply [::e/env ::e/raw-env] {}) a))

(deftest agent-get-build
  (match
   (run (arg
         {:k8s cfg
          :env {:build-id bid :BUILD_ID bid}}))
   {::sut/build  {:metadata {:name bid}
                  :test true
                  :repository rid}
    :ci3.repo.core/repository {:metadata {:name rid}}
    :checkout   hashcommit
    ::sut/build-config {:description "build in root"}})

  (match
   (run (arg
         {:k8s cfg
          :env {:build-id bid-src :BUILD_ID bid-src}}))
   {::sut/build  {:metadata {:name bid-src}
                  :repository rid-src}
    :ci3.repo.core/repository {:metadata {:name rid-src}}
    :checkout   hashcommit
    ::sut/build-config {:description "build in src"}})

  (match
   (run (arg
         {:k8s cfg
          :env {:build-id ghbid :BUILD_ID ghbid}}))
   {::sut/build  {:metadata {:name ghbid}
                  :repository ghrid}
    :ci3.repo.core/repository {:metadata {:name ghrid}}
    :checkout   github-hashcommit
    ::sut/build-config {:description "build in src"}})


  (match
   (run (arg
         {:k8s cfg
          :env {:build-id bid-private :BUILD_ID bid-private}}))
   {::sut/build  {:metadata {:name bid-private}
                  :test true
                  :repository rid-private}
    :ci3.repo.core/repository {:metadata {:name rid-private}}
    :checkout   bb-private-hashcommit
    ::sut/build-config {:description "private repo"}})

  )


