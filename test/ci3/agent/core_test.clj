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
(def rid "ci3public")
(def hashcommit "9db48d46ba032c0babeca534819ea0b3e09b8995")
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
               :url "https://bitbucket.org/Aitem/ci3-public"
               ;;:oauthConsumer
               #_{:key
                  {:valueFrom {:secretKeyRef {:name "bitbucket" :key "key"}}}
                  :secret
                  {:valueFrom {:secretKeyRef {:name "bitbucket" :key "secret"}}}}
               })

  (f)
  (k8s/delete cfg :builds bid)
  (k8s/delete cfg :repositories rid))

(use-fixtures :once agent-fixture)

(deftest agent-get-build
  (match
   (sut/run {:k8s cfg
             :env {:build-id bid :BUILD_ID bid}})
   {::sut/build  {:metadata {:name bid}
                  :repository rid}
    ::ci3.repo.core/repository {:metadata {:name rid}}
    :checkout   hashcommit
    ;;::sut/build-config {:kind "Build"}
    }))



#_(deftest agent-checkout
  (match
   (sut/run {:env {:build-id bid}})
   {::sut/build      {:metadata {:name bid}}
    ::sut/repository {:metadata {:name repo/rid}}
    ::sut/checkout   hashcommit
    ::sut/build-config {:kind "Build" }}
   ))
