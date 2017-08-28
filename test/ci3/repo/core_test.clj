(ns ci3.repo.core-test
  (:require [ci3.repo.core :as sut]
            [matcho.core :refer [match]]
            [ci3.k8s :as k8s]
            [clojure.java.io :as io]
            [cheshire.core :as json]
            [unifn.core :as u]
            [clojure.test :refer :all]))

(def bb_rid "ci3public")
(def gh_rid "ci3githubpublic")
(def bb_prid "ci3githubpublic")
(def cfg {:apiVersion "ci3.io/v1" :ns "test"})

(defn repo-fixture [f]
  (k8s/create cfg :repositories
              {:apiVersion "ci3.io/v1"
               :kind "Repository"
               :metadata {:name bb_rid}
               :type "bitbucket"
               :fullName "Aitem/ci3-public"
               :url "https://bitbucket.org/Aitem/ci3-public"})

  (k8s/create cfg :repositories
              {:apiVersion "ci3.io/v1"
               :kind "Repository"
               :metadata {:name gh_rid}
               :type "github"
               :fullName "Aitem/ci3-github-public"
               :url "https://bitbucket.org/Aitem/ci3-public"})

  (k8s/create cfg :repositories
              {:apiVersion "ci3.io/v1"
               :kind "Repository"
               :metadata {:name bb_prid}
               :type "bitbucket"
               :fullName "Aitem/private-test"
               :url "https://bitbucket.org/Aitem/private-test"})

  (f)
  (k8s/delete cfg :repositories bb_rid)
  (k8s/delete cfg :repositories gh_rid)
  (k8s/delete cfg :repositories bb_prid))

(use-fixtures :once repo-fixture)

(deftest fetch-webhook
  (match
   (u/*apply
    ::sut/webhook
    {:k8s cfg
     :request {:route-params {:id "some-fake-id"}}})
   {:response {:status 400
               :body "Hook not found"}})

  (match
   (u/*apply
    ::sut/webhook
    {:k8s cfg
     :request {:route-params {:id bb_rid}}})
   {:response {:status 400
               :body "Invalid payload"}})

  (let [build (u/*apply
               ::sut/webhook
               {:k8s cfg
                :request {:body (slurp (io/resource "bb_hook.json"))
                          :route-params {:id bb_rid}}})]
    (match
     build
     {:response {:status 200
                 :body (fn [b]
                         (let [resp (json/parse-string b keyword)]
                           (and (= bb_rid (:repository resp)))))}})

    (k8s/delete k8s/cfg :builds (get-in build [::sut/build :metadata :name])))


  (let [build (u/*apply
               ::sut/webhook
               {:k8s cfg
                :request {:body (slurp (io/resource "bb_hook.json"))
                          :route-params {:id bb_prid}}})]
    (match
     build
     {:response {:status 200
                 :body (fn [b]
                         (let [resp (json/parse-string b keyword)]
                           (and (= bb_prid (:repository resp)))))}})

    (k8s/delete k8s/cfg :builds (get-in build [::sut/build :metadata :name])))


  (let [build (u/*apply
               ::sut/webhook
               {:k8s cfg
                :request {:body (slurp (io/resource "gh_hook.json"))
                          :route-params {:id gh_rid}}})]
    (match
     build
     {:response {:status 200
                 :body (fn [b]
                         (let [resp (json/parse-string b keyword)]
                           (and (= gh_rid (:repository resp)))))}})

    (k8s/delete k8s/cfg :builds (get-in build [::sut/build :metadata :name])))

  )


(comment
  (json/parse-string (slurp (io/resource "bb_hook.json")) keyword)
  )
