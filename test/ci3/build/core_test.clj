(ns ci3.build.core-test
  (:require [ci3.build.core :as sut]
            [matcho.core :refer [match]]
            [ci3.k8s :as k8s]
            [clojure.walk :as walk]
            [clojure.java.io :as io]
            [cheshire.core :as json]
            [unifn.core :as u]
            [clojure.test :refer :all]))

(def rid "ci3public")
(def cfg {:prefix "api" :apiVersion "v1" :ns "test"})
(defn build []
  {:apiVersion "ci3.io/v1"
   :kind "Build"
   :metadata {:name (str rid "-" (System/currentTimeMillis))}
   :status "pending"
   :hashcommit "some-hash"
   :repository rid})

(deftest create-build-pod
  (let [b (build)
        pod (::sut/pod (u/*apply
                        [::sut/build]
                        {:k8s cfg
                         :resource {:type "ADDED" :object b}}))
        pod (walk/keywordize-keys pod)
        envs (get-in pod [:spec :containers 0 :env])]
    (doseq [{name :name val :value} envs]
      (condp = name
        "BUILD_ID" (is (= val (get-in b [:metadata :name])))
        "REPOSITORY" (is (= val rid))
        true))
    (println pod)
    (match
     pod
     {:kind "Pod"
      :status {:phase "Pending"}})
    (k8s/delete cfg :pods (get-in pod [:metadata :name]))))
