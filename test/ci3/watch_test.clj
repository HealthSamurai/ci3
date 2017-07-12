(ns ci3.watch-test
  (:require [ci3.server.watch :as sut]
            [clojure.test :refer :all]
            [matcho.core :as matcho]))

(def env {:kube-url "http://localhost:8001"})

(deftest watch-test

  (matcho/match
   (sut/build-watch-query
    env
    {:ns "default"
     :apiVersion "ci3.io/v1"
     :resource :repositories})
   {:url "http://localhost:8001/apis/ci3.io/v1/namespaces/default/repositories"})

  (matcho/match
   (sut/build-watch-query
    env
    {:ns "default"
     :apiVersion "ci3.io/v1"
     :resource :builds})
   {:url "http://localhost:8001/apis/ci3.io/v1/namespaces/default/builds"})

  (matcho/match
   (sut/build-watch-query
    env
    {:ns "system"
     :apiVersion "ups/v2"
     :resource :builds})
   {:url "http://localhost:8001/apis/ups/v2/namespaces/system/builds"}))
